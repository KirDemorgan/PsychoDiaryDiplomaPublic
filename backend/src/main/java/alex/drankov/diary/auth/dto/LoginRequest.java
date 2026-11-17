package alex.drankov.diary.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,
        @NotBlank
        @Size(max = 72)
        @Schema(format = "password")
        String password
) {
    @Override
    public String toString() {
        return "Login[redacted]";
    }
}
