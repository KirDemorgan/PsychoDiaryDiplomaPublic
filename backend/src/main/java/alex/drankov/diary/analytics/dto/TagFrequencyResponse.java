package alex.drankov.diary.analytics.dto;

import alex.drankov.diary.tags.entity.TagCode;

public record TagFrequencyResponse(
        TagCode code,
        long occurrences,
        long days
) {
}
