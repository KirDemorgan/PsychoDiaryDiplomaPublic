package alex.drankov.diary.tags.dto;

import alex.drankov.diary.tags.entity.TagCode;

public record TagDefinitionResponse(
        TagCode code,
        String labelRu,
        String category
) {
}
