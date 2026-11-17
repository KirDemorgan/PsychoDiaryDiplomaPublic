package alex.drankov.diary.auth.service;

import alex.drankov.diary.auth.dto.LoginRequest;
import alex.drankov.diary.auth.dto.RegisterRequest;
import alex.drankov.diary.auth.entity.AuthSession;
import alex.drankov.diary.auth.entity.RefreshToken;
import alex.drankov.diary.auth.repository.AuthSessionRepository;
import alex.drankov.diary.auth.repository.RefreshTokenRepository;
import alex.drankov.diary.auth.security.Tokens;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.crypto.Hashing;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.mapper.UserMapper;
import alex.drankov.diary.users.repository.UserRepository;
import alex.drankov.diary.users.repository.UserSettingsRepository;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static alex.drankov.diary.TestFixtures.CLOCK;
import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.security;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    UserRepository users;
    @Mock
    UserSettingsRepository settings;
    @Mock
    AuthSessionRepository sessions;
    @Mock
    RefreshTokenRepository refresh;
    @Mock
    PasswordEncoder passwords;
    @Mock
    Tokens tokens;
    @Mock
    TransactionTemplate transactions;
    AuthService service;
    User owner;

    @BeforeEach
    void setup() {
        when(passwords.encode("dummy-password-for-timing-only")).thenReturn("dummy-hash");
        service = new AuthService(users, settings, Mappers.getMapper(UserMapper.class), sessions, refresh, passwords, tokens, security(), CLOCK, transactions);
        owner = user();
    }

    void transaction() {
        when(transactions.execute(any())).thenAnswer(call -> {
            TransactionCallback<?> callback = call.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    void issue() {
        when(tokens.refresh()).thenReturn("new-opaque-token");
        when(tokens.access(eq(owner), any())).thenReturn("access");
    }

    @Test
    @DisplayName("Регистрация нормализует email, хеширует пароль и создаёт настройки")
    void registerNormalizesEmailHashesPasswordAndCreatesSettings() {
        transaction();
        when(passwords.encode("long-demo-password")).thenReturn("hash");
        when(users.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        when(sessions.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        when(tokens.refresh()).thenReturn("new-token");
        when(tokens.access(any(), any())).thenReturn("access");
        var result = service.register(new RegisterRequest("Demo@Example.test", "long-demo-password", null, "UTC"));
        assertThat(result.user().email()).isEqualTo("demo@example.test");
        assertThat(result.expiresIn()).isEqualTo(900);
        verify(settings).saveAndFlush(any());
        var saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refresh).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(Hashing.sha256("new-token"));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo("new-token");
    }

    @Test
    @DisplayName("Повторная регистрация отклоняется без создания настроек и сессии")
    void duplicateAccountFailsBeforeHashAndSessions() {
        transaction();
        when(users.existsByEmail("demo@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("demo@example.test", "long-demo-password", null, "UTC"))).isInstanceOf(ApiException.class);
        verifyNoInteractions(sessions, settings);
    }

    @Test
    @DisplayName("Неизвестный аккаунт и неверный пароль возвращают одинаковую ошибку входа")
    void badCredentialsHaveSameErrorForMissingOrWrongPassword() {
        transaction();
        when(users.findByEmail("demo@example.test")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("demo@example.test", "wrong")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(401));
        verify(passwords).matches("wrong", "dummy-hash");
        when(users.findByEmail("demo@example.test")).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.login(new LoginRequest("demo@example.test", "wrong"))).isInstanceOf(ApiException.class);
        verify(passwords).matches("wrong", "hash");
        verifyNoInteractions(sessions);
    }

    @Test
    @DisplayName("Успешный вход создаёт новую сессию")
    void loginCreatesNewSession() {
        transaction();
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(passwords.matches("good", "hash")).thenReturn(true);
        when(users.lockById(owner.getId())).thenReturn(Optional.of(owner));
        when(sessions.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        issue();
        assertThat(service.login(new LoginRequest(owner.getEmail(), "good")).accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("Три устройства входят в один аккаунт, выход отзывает только одну сессию")
    void threeDevicesShareOwnerButLogoutRevokesOnlyOneSession() {
        transaction();
        when(users.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(passwords.matches("good", "hash")).thenReturn(true);
        when(users.lockById(owner.getId())).thenReturn(Optional.of(owner));
        var issuedSessions = new ArrayList<AuthSession>();
        when(sessions.saveAndFlush(any())).thenAnswer(call -> {
            AuthSession session = persisted(call.getArgument(0));
            issuedSessions.add(session);
            return session;
        });
        when(tokens.refresh()).thenReturn("phone-one", "phone-two", "phone-three");
        when(tokens.access(eq(owner), any())).thenReturn("access");
        for (int device = 0; device < 3; device++) {
            assertThat(service.login(new LoginRequest(owner.getEmail(), "good")).user().id())
                    .isEqualTo(owner.getId());
        }
        assertThat(issuedSessions).extracting(AuthSession::getId).doesNotHaveDuplicates();
        verify(refresh, times(3)).save(any());
        doAnswer(call -> {
            Consumer<TransactionStatus> callback = call.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        var first = issuedSessions.getFirst();
        var token = new RefreshToken(first.getId(), Hashing.sha256("phone-one"), first.getExpiresAt());
        when(refresh.ownerByHash(token.getTokenHash())).thenReturn(Optional.of(owner.getId()));
        when(refresh.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(sessions.findById(first.getId())).thenReturn(Optional.of(first));
        service.logout("phone-one");
        assertThat(first.active(NOW)).isFalse();
        assertThat(issuedSessions.subList(1, 3)).allMatch(session -> session.active(NOW));
    }

    @Test
    @DisplayName("Refresh заменяет токен, повтор использованного токена отзывает сессию до ошибки")
    void refreshRotatesAndReuseRevokesSessionBeforeFailure() {
        transaction();
        issue();
        var session = persisted(new AuthSession(owner.getId(), NOW.plusSeconds(86400)));
        var token = persisted(new RefreshToken(session.getId(), Hashing.sha256("old"), session.getExpiresAt()));
        when(refresh.ownerByHash(Hashing.sha256("old"))).thenReturn(Optional.of(owner.getId()));
        when(users.lockById(owner.getId())).thenReturn(Optional.of(owner));
        when(refresh.findByTokenHash(Hashing.sha256("old"))).thenReturn(Optional.of(token));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        assertThat(service.refresh("old").refreshToken()).isEqualTo("new-opaque-token");
        assertThat(token.getConsumedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(ApiException.class);
        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        verify(sessions).flush();
    }

    @Test
    @DisplayName("Истёкшая сессия не позволяет обновить токены")
    void expiredSessionCannotRefresh() {
        transaction();
        var session = persisted(new AuthSession(owner.getId(), NOW.minusSeconds(1)));
        var token = persisted(new RefreshToken(session.getId(), Hashing.sha256("old"), session.getExpiresAt()));
        when(refresh.ownerByHash(Hashing.sha256("old"))).thenReturn(Optional.of(owner.getId()));
        when(users.lockById(owner.getId())).thenReturn(Optional.of(owner));
        when(refresh.findByTokenHash(Hashing.sha256("old"))).thenReturn(Optional.of(token));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(ApiException.class);
        verify(tokens, never()).refresh();
    }

    @Test
    @DisplayName("Выход отзывает сессию и допускает неизвестный refresh-токен")
    void logoutRevokesSessionAndIsSafeForUnknownToken() {
        doAnswer(call -> {
            Consumer<TransactionStatus> callback = call.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        })
                .when(transactions).executeWithoutResult(any());
        var session = persisted(new AuthSession(owner.getId(), NOW.plusSeconds(86400)));
        var token = new RefreshToken(session.getId(), Hashing.sha256("old"), session.getExpiresAt());
        when(refresh.ownerByHash(Hashing.sha256("old"))).thenReturn(Optional.of(owner.getId()));
        when(users.lockById(owner.getId())).thenReturn(Optional.of(owner));
        when(refresh.findByTokenHash(Hashing.sha256("old"))).thenReturn(Optional.of(token));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        service.logout("old");
        assertThat(session.active(NOW)).isFalse();
        service.logout("unknown");
    }
}
