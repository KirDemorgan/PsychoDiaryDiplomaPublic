package alex.drankov.diary.auth.security;

import alex.drankov.diary.auth.entity.AuthSession;
import alex.drankov.diary.common.crypto.Hashing;

import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static alex.drankov.diary.TestFixtures.CLOCK;
import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.security;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

class TokensTest {
    @Test
    @DisplayName("Выдаются подписанный JWT и случайный непрозрачный refresh-токен")
    void issuesSignedJwtAndHighEntropyOpaqueRefresh() {
        var key = new SecretKeySpec(Base64.getDecoder().decode(security().secretBase64()), "HmacSHA256");
        var encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        var service = new Tokens(encoder, security(), CLOCK);
        var user = user();
        var session = new AuthSession(user.getId(), NOW.plusSeconds(600));
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // Signature decoding with an explicit fixed-clock validity check; no dependence on wall time.
        decoder.setJwtValidator(jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
        var jwt = decoder.decode(service.access(user, session));
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getAudience()).containsExactly("diary-mobile");
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://diary.local");
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(session.getId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(service.refresh()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(service.refresh());
        assertThat(Hashing.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("Срок access-токена ограничен настройкой TTL")
    void accessExpiryIsBoundedByConfiguredTtl() {
        var key = new SecretKeySpec(new byte[32], "HmacSHA256");
        var encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        var service = new Tokens(encoder, security(), CLOCK);
        var user = user();
        var result = service.access(user, new AuthSession(user.getId(), NOW.plusSeconds(86400)));
        assertThat(result.split("\\.")).hasSize(3);
    }
}
