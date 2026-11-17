package alex.drankov.diary.reports.dto;

import alex.drankov.diary.reports.entity.FactType;

import java.util.List;

public record FactResponse(
        String factId,
        FactType type,
        FactValueResponse value,
        List<SourceRefResponse> sourceRefs
) {
}
