package alex.drankov.diary.analytics.dto;

import alex.drankov.diary.tags.dto.TagResponse;

import java.util.UUID;

public record ContextTagResponse(
        UUID entryId,
        int revision,
        TagResponse tag
) {
}
