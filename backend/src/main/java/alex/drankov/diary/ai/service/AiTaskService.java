package alex.drankov.diary.ai.service;

import alex.drankov.diary.ai.adapter.ModelAdapter;
import alex.drankov.diary.ai.dto.AnalysisResponse;
import alex.drankov.diary.ai.dto.CreateAnalysisRequest;
import alex.drankov.diary.ai.entity.AiKind;
import alex.drankov.diary.ai.entity.AiStatus;
import alex.drankov.diary.ai.entity.AiTask;
import alex.drankov.diary.ai.repository.AiTaskRepository;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.crypto.Hashing;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.reports.entity.Report;
import alex.drankov.diary.users.service.UserAccess;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiTaskService {
    private final AiTaskRepository tasks;
    private final EntryRepository entries;
    private final UserAccess access;
    private final AiConsent consent;
    private final AnalysisQuery query;
    private final Idempotency idempotency;
    private final ModelAdapter model;
    private final Clock clock;

    @Transactional
    public MutationResult<AnalysisResponse> start(
            UUID user,
            UUID entryId,
            UUID key,
            CreateAnalysisRequest request
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "POST /entries/" + entryId + "/analyses", key, request, AnalysisResponse.class, () -> {
            var entry = entries.findByIdAndUserId(entryId, user).orElseThrow(ApiException::notFound);
            consent.require(user);
            if (request.revision() == null || request.revision() != entry.getRevision()) {
                throw new ApiException(409, "STALE_REVISION", "Редакция записи изменилась");
            }
            if (entry.isReviewComplete()) {
                throw new ApiException(409, "REVIEW_ALREADY_COMPLETED", "Проверенная редакция не анализируется повторно");
            }
            var task = tasks.findFirstByUserIdAndEntryIdAndRevisionOrderByCreatedAtDescIdDesc(user, entryId, entry.getRevision())
                    .filter(value -> Set.of(AiStatus.QUEUED, AiStatus.RUNNING, AiStatus.SUCCEEDED).contains(value.getStatus()))
                    .orElseGet(() -> tasks.saveAndFlush(new AiTask(user, entryId, null, entry.getRevision(), owner.getDataRevision(),
                            entryHash(entry), model.modelId(), AiKind.ENTRY)));
            return new MutationResult<>(query.response(task, entry.getRevision()), entryId, task.getVersion(), 202);
        });
    }

    @Transactional
    public MutationResult<AnalysisResponse> cancel(
            UUID user,
            UUID entryId,
            UUID id,
            UUID key
    ) {
        access.lock(user);
        return idempotency.execute(user, "POST /entries/" + entryId + "/analyses/" + id + "/cancel", key, id,
                AnalysisResponse.class, () -> {
                    var entry = entries.findByIdAndUserId(entryId, user).orElseThrow(ApiException::notFound);
                    var task = tasks.findByIdAndUserId(id, user).filter(value -> entryId.equals(value.getEntryId())).orElseThrow(ApiException::notFound);
                    if (!Set.of(AiStatus.QUEUED, AiStatus.RUNNING, AiStatus.CANCELLED).contains(task.getStatus())) {
                        throw new ApiException(409, "ANALYSIS_NOT_CANCELLABLE", "Задача уже завершена");
                    }
                    task.finish(AiStatus.CANCELLED, "USER_CANCELLED", clock.instant());
                    tasks.flush();
                    return new MutationResult<>(query.response(task, entry.getRevision()), entryId, task.getVersion(), 200);
                });
    }

    @Transactional
    public void enqueueReport(Report report) {
        access.lock(report.getUserId());
        consent.require(report.getUserId());
        tasks.save(new AiTask(report.getUserId(), null, report.getId(), null, report.getSourceDataRevision(),
                report.getInputHash(), model.modelId(), AiKind.REPORT));
        report.queue();
    }

    @Transactional
    public void cancelReport(
            UUID user,
            UUID reportId
    ) {
        tasks.findFirstByUserIdAndReportIdOrderByCreatedAtDescIdDesc(user, reportId)
                .filter(value -> Set.of(AiStatus.QUEUED, AiStatus.RUNNING).contains(value.getStatus()))
                .ifPresent(value -> value.finish(AiStatus.CANCELLED, "USER_CANCELLED", clock.instant()));
    }

    public static String entryHash(DiaryEntry entry) {
        return Hashing.sha256(entry.getRevision() + "|" + entry.getText() + "|" + entry.getOccurredAt() + "|" + entry.getTimeZone());
    }
}
