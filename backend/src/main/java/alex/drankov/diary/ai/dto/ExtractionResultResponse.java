package alex.drankov.diary.ai.dto;

import java.util.List;

public record ExtractionResultResponse(
        List<CandidateResponse> candidates
) {
}
