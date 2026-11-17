package alex.drankov.diary.ai.service;

import alex.drankov.diary.ai.dto.CandidateResponse;
import alex.drankov.diary.ai.entity.AiKind;
import alex.drankov.diary.ai.entity.AiStatus;
import alex.drankov.diary.ai.entity.AiTask;
import alex.drankov.diary.ai.entity.AnalysisCandidate;
import alex.drankov.diary.ai.repository.AiTaskRepository;
import alex.drankov.diary.ai.repository.AnalysisCandidateRepository;
import alex.drankov.diary.ai.service.model.AiWorkItem;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.reports.dto.FactResponse;
import alex.drankov.diary.reports.dto.ReportOverviewResponse;
import alex.drankov.diary.reports.dto.ReportSnapshot;
import alex.drankov.diary.reports.entity.FactType;
import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.reports.entity.ReportStatus;
import alex.drankov.diary.reports.repository.ReportRepository;
import alex.drankov.diary.reports.service.TemplateOverview;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.service.UserAccess;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class AiQueue {
    /** Short fact identifier shown to the model instead of a 66-character hash. */
    public static String alias(int index) {
        return "F" + (index + 1);
    }

    private final AiTaskRepository tasks;
    private final AnalysisCandidateRepository candidates;
    private final EntryRepository entries;
    private final ReportRepository reports;
    private final UserAccess access;
    private final AiConsent consent;
    private final TemplateOverview template;
    private final JsonMapper json;
    private final Clock clock;

    @Value("${app.ai.timeout:PT30S}")
    private Duration timeout;

    @Transactional
    public AiWorkItem claim() {
        var available = tasks.runnable(clock.instant(), PageRequest.of(0, 1));
        if (available.isEmpty()) {
            return null;
        }
        var identity = available.getFirst();
        User owner = lock(identity.userId());
        if (owner == null) {
            return null;
        }
        var task = tasks.findByIdAndUserId(identity.id(), owner.getId()).orElse(null);
        if (task == null || !task.runnable(clock.instant()) || invalidate(task, owner)) {
            return null;
        }
        if (task.getAttempt() >= 2) {
            terminalFailure(task, "WORKER_INTERRUPTED");
            return null;
        }
        var token = UUID.randomUUID();
        task.claim(token, clock.instant().plus(timeout).plusSeconds(30));
        String text = null;
        String input;
        List<FactResponse> facts = List.of();
        if (task.getKind() == AiKind.ENTRY) {
            var entry = entries.findByIdAndUserId(task.getEntryId(), owner.getId()).orElseThrow(ApiException::notFound);
            text = entry.getText();
            input = json.writeValueAsString(Map.of("text", text, "occurredAt", entry.getOccurredAt(),
                    "timeZone", entry.getTimeZone(), "revision", entry.getRevision()));
        } else {
            var report = reports.findByIdAndUserId(task.getReportId(), owner.getId()).orElseThrow(ApiException::notFound);
            var snapshot = json.readValue(report.getSnapshotJson(), ReportSnapshot.class);
            facts = snapshot.facts().stream().filter(fact -> fact.type() != FactType.QUOTE).toList();
            var permitted = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < facts.size(); i++) {
                var fact = facts.get(i);
                permitted.add(Map.of("id", alias(i), "type", fact.type(), "value", fact.value(),
                        "allowedText", template.sentence(fact)));
            }
            input = json.writeValueAsString(permitted);
            report.running();
        }
        tasks.flush();
        return new AiWorkItem(task.getId(), token, task.getKind(), input, text, facts, task.getModelId(),
                task.getPromptVersion(), task.getSchemaVersion());
    }

    @Transactional
    public void succeed(
            AiWorkItem work,
            List<CandidateResponse> result,
            ReportOverviewResponse overview
    ) {
        var task = publishing(work);
        if (task == null) {
            return;
        }
        if (task.getKind() == AiKind.ENTRY) {
            candidates.saveAllAndFlush(result.stream().map(value -> new AnalysisCandidate(task, value)).toList());
        } else {
            var report = reports.findByIdAndUserId(task.getReportId(), task.getUserId()).orElseThrow(ApiException::notFound);
            var snapshot = json.readValue(report.getSnapshotJson(), ReportSnapshot.class);
            report.ready(json.writeValueAsString(new ReportSnapshot(snapshot.formatVersion(), snapshot.selection(), snapshot.facts(),
                    Objects.requireNonNull(overview), snapshot.previewData(), OverviewMode.AI, List.of())), null);
        }
        task.finish(AiStatus.SUCCEEDED, null, clock.instant());
    }

    @Transactional
    public void fail(
            AiWorkItem work,
            String code
    ) {
        var task = publishing(work);
        if (task == null) {
            return;
        }
        boolean permanent = Set.of("MODEL_NOT_CONFIGURED", "MODEL_CHANGED", "PROMPT_VERSION_UNSUPPORTED").contains(code)
                || task.getKind() == AiKind.REPORT && "INVALID_MODEL_RESULT".equals(code);
        if (task.getAttempt() < 2 && !permanent) {
            task.retry(code);
            if (task.getKind() == AiKind.REPORT) {
                reports.findByIdAndUserId(task.getReportId(), task.getUserId()).ifPresent(value -> value.queue());
            }
        } else {
            terminalFailure(task, code);
        }
    }

    private AiTask publishing(AiWorkItem work) {
        var identity = tasks.identity(work.taskId()).orElse(null);
        if (identity == null) {
            return null;
        }
        var owner = lock(identity.userId());
        if (owner == null) {
            return null;
        }
        var task = tasks.findByIdAndUserId(identity.id(), owner.getId()).orElse(null);
        if (task == null || task.getStatus() != AiStatus.RUNNING || !work.leaseToken().equals(task.getLeaseToken())
                || task.getLeaseUntil() == null || !task.getLeaseUntil().isAfter(clock.instant()) || invalidate(task, owner)) {
            return null;
        }
        return task;
    }

    private boolean invalidate(
            AiTask task,
            User owner
    ) {
        if (!consent.allowed(owner.getId())) {
            task.finish(AiStatus.CANCELLED, "AI_DISABLED", clock.instant());
            cancelReport(task);
            return true;
        }
        if (task.getKind() == AiKind.ENTRY) {
            var entry = entries.findByIdAndUserId(task.getEntryId(), owner.getId()).orElse(null);
            if (entry == null || task.getRevision() != entry.getRevision() || !task.getInputHash().equals(AiTaskService.entryHash(entry))) {
                task.finish(AiStatus.STALE, "SOURCE_DATA_CHANGED", clock.instant());
                return true;
            }
            if (entry.isReviewComplete()) {
                task.finish(AiStatus.CANCELLED, "REVIEW_ALREADY_COMPLETED", clock.instant());
                return true;
            }
        } else {
            var report = reports.findByIdAndUserId(task.getReportId(), owner.getId()).orElse(null);
            if (report == null || report.getSnapshotJson() == null || owner.getDataRevision() != task.getSourceDataRevision()
                    || !task.getInputHash().equals(report.getInputHash()) || !Set.of(ReportStatus.QUEUED, ReportStatus.RUNNING).contains(report.getStatus())) {
                task.finish(AiStatus.STALE, "SOURCE_DATA_CHANGED", clock.instant());
                cancelReport(task);
                return true;
            }
        }
        return false;
    }

    private void terminalFailure(
            AiTask task,
            String code
    ) {
        task.finish(AiStatus.FAILED, code, clock.instant());
        if (task.getKind() == AiKind.REPORT) {
            var report = reports.findByIdAndUserId(task.getReportId(), task.getUserId()).orElseThrow(ApiException::notFound);
            var snapshot = json.readValue(report.getSnapshotJson(), ReportSnapshot.class);
            report.ready(json.writeValueAsString(new ReportSnapshot(snapshot.formatVersion(), snapshot.selection(), snapshot.facts(),
                    template.build(snapshot.facts()), snapshot.previewData(), OverviewMode.TEMPLATE,
                    List.of("TEMPLATE_OVERVIEW", code))), code);
        }
    }

    private void cancelReport(AiTask task) {
        if (task.getReportId() != null) {
            reports.findByIdAndUserId(task.getReportId(), task.getUserId()).ifPresent(value -> value.cancel());
        }
    }

    private User lock(UUID id) {
        try {
            return access.lock(id);
        } catch (ApiException exception) {
            if (exception.getStatus() != 404) {
                throw exception;
            }
            return null;
        }
    }
}
