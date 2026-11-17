package alex.drankov.diary.ai.dto;

import java.util.List;

public record ModelNarrativeClaim(
        String text,
        List<String> factIds
) {
}
