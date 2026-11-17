package alex.drankov.diary.reports.dto;

import alex.drankov.diary.reports.entity.OverviewMode;

public record OverviewProvenanceResponse(
        OverviewMode mode,
        String modelId,
        String promptVersion,
        String schemaVersion
) {
}
