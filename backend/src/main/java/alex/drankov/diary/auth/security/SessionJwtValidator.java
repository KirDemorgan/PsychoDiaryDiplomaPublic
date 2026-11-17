package alex.drankov.diary.auth.security;

import alex.drankov.diary.auth.repository.AuthSessionRepository;
import alex.drankov.diary.users.repository.UserRepository;

import java.time.Clock;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionJwtValidator implements OAuth2TokenValidator<Jwt> {
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final Clock clock;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            UUID user = UUID.fromString(jwt.getSubject());
            UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
            boolean valid = jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && sessions.findById(sessionId).filter(
                    s -> s.getUserId().equals(user) && s.active(clock.instant())).isPresent()
                    && users.existsById(user);
            if (valid) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | NullPointerException invalidClaims) {
            // Malformed claims are authentication failures, not server errors.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Сессия недоступна", null));
    }
}
