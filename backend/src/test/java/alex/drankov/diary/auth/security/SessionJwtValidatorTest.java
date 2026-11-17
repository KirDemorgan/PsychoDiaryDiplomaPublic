package alex.drankov.diary.auth.security;

import alex.drankov.diary.auth.entity.AuthSession;
import alex.drankov.diary.auth.repository.AuthSessionRepository;
import alex.drankov.diary.users.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static alex.drankov.diary.TestFixtures.CLOCK;
import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.security;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionJwtValidatorTest {
    @Test
    @DisplayName("JWT отклоняется для неверной, удалённой, отозванной, истёкшей или чужой сессии")
    void rejectsMalformedDeletedRevokedExpiredAndForeignSessions() {
        var sessions = mock(AuthSessionRepository.class);
        var users = mock(UserRepository.class);
        var validator = new SessionJwtValidator(sessions, users, CLOCK);
        var user = user();
        var session = new AuthSession(user.getId(), NOW.plusSeconds(900));
        var jwt = Jwt.withTokenValue("value").header("alg", "HS256").subject(user.getId().toString())
                .claim("sid", session.getId().toString()).issuedAt(NOW).expiresAt(NOW.plusSeconds(300)).build();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(users.existsById(user.getId())).thenReturn(true);
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
        when(users.existsById(user.getId())).thenReturn(false);
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
        session.revoke(NOW);
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(new AuthSession(UUID.randomUUID(), NOW.plusSeconds(900))));
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
        when(sessions.findById(session.getId())).thenReturn(Optional.of(new AuthSession(user.getId(), NOW.minusSeconds(1))));
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
        var invalid = Jwt.withTokenValue("bad").header("alg", "HS256").subject("bad").build();
        assertThat(validator.validate(invalid).hasErrors()).isTrue();
    }
}
