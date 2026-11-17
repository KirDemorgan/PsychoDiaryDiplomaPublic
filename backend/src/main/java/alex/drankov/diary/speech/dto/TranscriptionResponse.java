package alex.drankov.diary.speech.dto;

import alex.drankov.diary.speech.entity.TranscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record TranscriptionResponse(
        UUID id,
        TranscriptionStatus status,
        String transcript,
        String modelId,
        long durationMillis,
        int attempt,
        Instant expiresAt,
        Instant finishedAt,
        String errorCode
) {
    @Override
    public String toString() {
        return "TranscriptionResponse[id=" + id + ", status=" + status + ", transcript=<redacted>]";
    }
}
