package alex.drankov.diary.common.idempotency.dto;

import java.util.UUID;

public record MutationResult<T>(T body, UUID resourceId, long version, int status) {
}
