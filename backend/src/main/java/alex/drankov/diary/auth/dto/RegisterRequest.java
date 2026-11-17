package alex.drankov.diary.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,
        @NotBlank
        @Size(min = 12, max = 72)
        @Schema(format = "password", minLength = 12, maxLength = 72)
        String password,
        @Size(max = 100)
        String displayName,
        @NotBlank
        String timeZone
) {
    @Override
    public String toString() {
        return "Register[redacted]";
    }
}
