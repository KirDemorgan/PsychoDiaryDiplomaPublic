package alex.drankov.diary.auth.security;

import alex.drankov.diary.auth.entity.AuthSession;
import alex.drankov.diary.security.SecurityProperties;
import alex.drankov.diary.users.entity.User;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Tokens {
    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public String access(User user, AuthSession session) {
        var now = clock.instant();
        var expiry = now.plus(properties.accessTtl());
        if (expiry.isAfter(session.getExpiresAt())) {
            expiry = session.getExpiresAt();
        }
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(user.getId().toString())
                .audience(List.of(properties.audience())).issuedAt(now).expiresAt(expiry)
                .id(UUID.randomUUID().toString()).claim("sid", session.getId().toString())
                .claim("roles", List.of(user.getRole())).build();
        return encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public String refresh() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
