package alex.drankov.diary.users.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String timeZone,
        long version,
        long dataRevision
) {
}
