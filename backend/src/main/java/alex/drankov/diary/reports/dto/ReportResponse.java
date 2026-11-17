package alex.drankov.diary.reports.dto;

import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.reports.entity.ReportStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        LocalDate from,
        LocalDate to,
        String timeZone,
        ReportStatus status,
        boolean stale,
        Instant createdAt,
        long sourceDataRevision,
        long version,
        OverviewMode overviewMode,
        List<String> warnings,
        CreateReportRequest selection,
        String inputHash,
        List<FactResponse> facts,
        ReportOverviewResponse overview,
        ReportPreviewResponse previewData,
        String errorCode
) {
}
