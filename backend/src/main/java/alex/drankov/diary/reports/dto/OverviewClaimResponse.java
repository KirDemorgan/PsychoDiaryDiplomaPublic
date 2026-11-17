package alex.drankov.diary.reports.dto;

import java.util.List;

public record OverviewClaimResponse(
        String id,
        String text,
        List<String> factIds,
        List<SourceRefResponse> sourceRefs
) {
}
