package alex.drankov.diary.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String secretBase64,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl,
        List<String> allowedOrigins,
        @NotNull Duration authWindow,
        @Min(1) int authMaxAttempts
) {
    public SecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream().filter(
                s -> !s.isBlank()).toList();
        if (accessTtl != null && (accessTtl.isNegative() || accessTtl.isZero() || accessTtl.compareTo(
                Duration.ofHours(1)) > 0)) {
            throw new IllegalArgumentException("Access TTL must be positive and <= 1 hour");
        }
        if (refreshTtl != null && (refreshTtl.isNegative() || refreshTtl.isZero())) {
            throw new IllegalArgumentException("Refresh TTL must be positive");
        }
        if (authWindow != null && (authWindow.isNegative() || authWindow.isZero())) {
            throw new IllegalArgumentException("Auth window must be positive");
        }
    }
}
