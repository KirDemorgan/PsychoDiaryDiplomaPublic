package alex.drankov.diary.reports.dto;

import alex.drankov.diary.analytics.dto.ComparisonResponse;
import alex.drankov.diary.analytics.dto.ContextTagResponse;
import alex.drankov.diary.analytics.dto.OverviewResponse;
import alex.drankov.diary.entries.dto.EntryResponse;
import alex.drankov.diary.ratings.dto.RatingResponse;

import java.util.List;

public record ReportPreviewResponse(
        OverviewResponse statistics,
        List<ComparisonResponse> comparisons,
        List<RatingResponse> ratings,
        List<EntryResponse> entries,
        List<ContextTagResponse> tags,
        List<ReportMedicationResponse> medications
) {
}
