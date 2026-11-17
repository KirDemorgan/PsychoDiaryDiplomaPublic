package alex.drankov.diary.reports.dto;

import java.util.List;

public record ReportOverviewResponse(
        String title,
        List<OverviewClaimResponse> claims,
        List<String> limitations,
        OverviewProvenanceResponse provenance
) {
}
