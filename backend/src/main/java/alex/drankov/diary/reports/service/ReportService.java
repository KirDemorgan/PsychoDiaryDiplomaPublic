package alex.drankov.diary.reports.service;

import alex.drankov.diary.ai.service.AiConsent;
import alex.drankov.diary.ai.service.AiTaskService;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.common.crypto.Hashing;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.reports.dto.CreateReportRequest;
import alex.drankov.diary.reports.dto.ReportInputSnapshot;
import alex.drankov.diary.reports.dto.ReportResponse;
import alex.drankov.diary.reports.dto.ReportSnapshot;
import alex.drankov.diary.reports.dto.ReportSummaryResponse;
import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.reports.entity.Report;
import alex.drankov.diary.reports.entity.ReportSection;
import alex.drankov.diary.reports.entity.ReportStatus;
import alex.drankov.diary.reports.render.ReportPdfRenderer;
import alex.drankov.diary.reports.repository.ReportRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportService {
    private final ReportRepository reports;
    private final ReportSnapshotBuilder snapshots;
    private final ReportPdfRenderer pdf;
    private final UserAccess access;
    private final Idempotency idempotency;
    private final JsonMapper json;
    private final AiTaskService ai;
    private final AiConsent consent;

    @Transactional
    public MutationResult<ReportResponse> create(
            UUID user,
            UUID key,
            CreateReportRequest request
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "POST /reports", key, request, ReportResponse.class, () -> {
            if (request.sections() != null && request.sections().contains(ReportSection.AI_OVERVIEW)) {
                consent.require(user);
            }
            var snapshot = snapshots.build(owner, request);
            String payload = json.writeValueAsString(snapshot);
            var selection = snapshot.selection();
            var report = reports.saveAndFlush(new Report(user, selection.from(), selection.to(), selection.timeZone(),
                    owner.getDataRevision(), Hashing.sha256(json.writeValueAsString(new ReportInputSnapshot(
                            selection, snapshot.facts(), snapshot.previewData()))), payload));
            if (selection.sections().contains(ReportSection.AI_OVERVIEW)) {
                ai.enqueueReport(report);
                reports.flush();
            }
            return new MutationResult<>(response(report, owner.getDataRevision()), report.getId(), report.getVersion(), 202);
        });
    }

    public ReportResponse get(
            UUID user,
            UUID id
    ) {
        return response(find(user, id), access.read(user).getDataRevision());
    }

    public PageResponse<ReportSummaryResponse> list(
            UUID user,
            int page,
            int size
    ) {
        long revision = access.read(user).getDataRevision();
        return PageResponse.of(reports.findByUserId(user,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))),
                report -> summary(report, revision));
    }

    public byte[] pdf(
            UUID user,
            UUID id,
            boolean includeAiOverview
    ) {
        var report = find(user, id);
        if (report.getSourceDataRevision() != access.read(user).getDataRevision()) {
            throw new ApiException(409, "REPORT_STALE", "Источники изменились, создайте новый отчёт");
        }
        if (report.getStatus() != ReportStatus.READY) {
            throw new ApiException(409, "REPORT_NOT_READY", "Отчёт ещё не готов");
        }
        return pdf.render(snapshot(report), includeAiOverview);
    }

    @Transactional
    public MutationResult<ReportResponse> cancel(
            UUID user,
            UUID id,
            UUID key
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "POST /reports/" + id + "/cancel", key, id, ReportResponse.class, () -> {
            var report = find(user, id);
            if (report.getStatus() != ReportStatus.QUEUED && report.getStatus() != ReportStatus.RUNNING
                    && report.getStatus() != ReportStatus.CANCELLED) {
                throw new ApiException(409, "REPORT_NOT_CANCELLABLE", "Готовый отчёт можно удалить");
            }
            report.cancel();
            ai.cancelReport(user, id);
            reports.flush();
            return new MutationResult<>(response(report, owner.getDataRevision()), id, report.getVersion(), 200);
        });
    }

    @Transactional
    public void delete(
            UUID user,
            UUID id,
            String ifMatch
    ) {
        access.lock(user);
        var report = find(user, id);
        Versions.require(ifMatch, report.getVersion());
        idempotency.erase(user, id);
        reports.delete(report);
    }

    private Report find(
            UUID user,
            UUID id
    ) {
        return reports.findByIdAndUserId(id, user).orElseThrow(ApiException::notFound);
    }

    private ReportSnapshot snapshot(Report report) {
        return json.readValue(report.getSnapshotJson(), ReportSnapshot.class);
    }

    private ReportSummaryResponse summary(
            Report report,
            long revision
    ) {
        var snapshot = report.getSnapshotJson() == null ? null : snapshot(report);
        return new ReportSummaryResponse(report.getId(), report.getFromDate(), report.getToDate(), report.getTimeZone(),
                report.getStatus(), report.getSourceDataRevision() != revision, report.getCreatedAt(),
                report.getSourceDataRevision(), report.getVersion(), snapshot == null ? OverviewMode.NONE : snapshot.overviewMode(),
                snapshot == null ? List.of("SOURCE_DATA_CHANGED") : snapshot.warnings());
    }

    private ReportResponse response(
            Report report,
            long revision
    ) {
        var snapshot = report.getSnapshotJson() == null ? null : snapshot(report);
        var summary = summary(report, revision);
        return new ReportResponse(summary.id(), summary.from(), summary.to(), summary.timeZone(), summary.status(), summary.stale(),
                summary.createdAt(), summary.sourceDataRevision(), summary.version(), summary.overviewMode(), summary.warnings(),
                snapshot == null ? null : snapshot.selection(), report.getInputHash(),
                snapshot == null ? List.of() : snapshot.facts(), snapshot == null ? null : snapshot.overview(),
                snapshot == null ? null : snapshot.previewData(), report.getErrorCode());
    }
}
