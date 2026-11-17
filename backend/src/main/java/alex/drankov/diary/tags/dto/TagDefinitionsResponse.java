package alex.drankov.diary.tags.dto;

import java.util.List;

public record TagDefinitionsResponse(
        String version,
        List<TagDefinitionResponse> items
) {
}
