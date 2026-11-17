package alex.drankov.diary.ai.dto;

import java.util.List;

public record ModelExtraction(
        List<ModelCandidate> candidates
) {
}
