package alex.drankov.diary.auth.dto;

import alex.drankov.diary.users.dto.UserResponse;

public record AuthResponse(
        UserResponse user,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    @Override
    public String toString() {
        return "AuthResponse[redacted]";
    }
}
