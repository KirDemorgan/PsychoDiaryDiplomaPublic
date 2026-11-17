package alex.drankov.diary.exports.dto;

import java.time.Instant;
import java.util.UUID;

public record ExportJobResponse(
        UUID id,
        String status,
        Instant createdAt,
        Instant expiresAt,
        long sourceDataRevision,
        String errorCode
) {
}
