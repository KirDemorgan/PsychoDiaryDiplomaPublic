package alex.drankov.diary.reports.dto;

import alex.drankov.diary.reports.entity.OverviewMode;

import java.util.List;

public record ReportSnapshot(
        String formatVersion,
        CreateReportRequest selection,
        List<FactResponse> facts,
        ReportOverviewResponse overview,
        ReportPreviewResponse previewData,
        OverviewMode overviewMode,
        List<String> warnings
) {
}
