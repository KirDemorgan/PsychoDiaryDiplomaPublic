package alex.drankov.diary.ai.dto;

import java.util.List;

public record ModelNarrative(
        List<ModelNarrativeClaim> claims
) {
}
