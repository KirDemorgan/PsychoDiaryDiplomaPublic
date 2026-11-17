package alex.drankov.diary.ai.dto;

import alex.drankov.diary.ai.entity.AiStatus;

import java.time.Instant;
import java.util.UUID;

public record AnalysisResponse(
        UUID id,
        UUID entryId,
        int revision,
        AiStatus status,
        int attempt,
        Instant createdAt,
        Instant finishedAt,
        String errorCode,
        ExtractionResultResponse result,
        String modelId,
        String promptVersion,
        String schemaVersion
) {
}
