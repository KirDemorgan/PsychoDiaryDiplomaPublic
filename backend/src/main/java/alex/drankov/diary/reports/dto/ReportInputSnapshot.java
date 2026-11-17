package alex.drankov.diary.reports.dto;

import java.util.List;

public record ReportInputSnapshot(
        CreateReportRequest selection,
        List<FactResponse> facts,
        ReportPreviewResponse previewData
) {
}
