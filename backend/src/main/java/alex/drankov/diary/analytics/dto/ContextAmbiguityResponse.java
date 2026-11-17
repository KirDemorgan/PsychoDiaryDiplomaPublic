package alex.drankov.diary.analytics.dto;

import alex.drankov.diary.tags.entity.TagCode;

import java.time.LocalDate;
import java.util.List;

public record ContextAmbiguityResponse(
        LocalDate date,
        List<TagCode> codes
) {
}
