package alex.drankov.diary.auth.service;

import alex.drankov.diary.auth.dto.AuthResponse;
import alex.drankov.diary.auth.dto.LoginRequest;
import alex.drankov.diary.auth.dto.RegisterRequest;
import alex.drankov.diary.auth.entity.AuthSession;
import alex.drankov.diary.auth.entity.RefreshToken;
import alex.drankov.diary.auth.repository.AuthSessionRepository;
import alex.drankov.diary.auth.repository.RefreshTokenRepository;
import alex.drankov.diary.auth.security.Tokens;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.TimeRules;
import alex.drankov.diary.common.crypto.Hashing;
import alex.drankov.diary.security.SecurityProperties;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.entity.UserSettings;
import alex.drankov.diary.users.mapper.UserMapper;
import alex.drankov.diary.users.repository.UserRepository;
import alex.drankov.diary.users.repository.UserSettingsRepository;
import alex.drankov.diary.users.service.ProfileService;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final UserMapper mapper;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final Tokens tokens;
    private final SecurityProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final String dummyHash;

    public AuthService(
            UserRepository users,
            UserSettingsRepository settings,
            UserMapper mapper,
            AuthSessionRepository sessions,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder passwords,
            Tokens tokens,
            SecurityProperties properties,
            Clock clock,
            TransactionTemplate transactions
    ) {
        this.users = users;
        this.settings = settings;
        this.mapper = mapper;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
        this.transactions = transactions;
        dummyHash = passwords.encode("dummy-password-for-timing-only");
    }

    public AuthResponse register(RegisterRequest request) {
        validatePassword(request.password());
        TimeRules.zone(request.timeZone());
        ProfileService.validateName(request.displayName());
        return transactions.execute(status -> {
            String email = normalize(request.email());
            if (users.existsByEmail(email)) {
                throw new ApiException(409, "ACCOUNT_CONFLICT", "Регистрация недоступна для указанного адреса");
            }
            var user = users.saveAndFlush(
                    new User(email, passwords.encode(request.password()), request.displayName(), request.timeZone()));
            settings.saveAndFlush(new UserSettings(user.getId()));
            return newSession(user);
        });
    }

    public AuthResponse login(LoginRequest request) {
        validatePassword(request.password());
        return transactions.execute(status -> {
            var candidate = users.findByEmail(normalize(request.email()));
            boolean matches = passwords.matches(request.password(),
                    candidate.map(User::getPasswordHash).orElse(dummyHash));
            if (candidate.isEmpty() || !matches) {
                throw invalidCredentials();
            }
            var user = users.lockById(candidate.get().getId()).orElseThrow(AuthService::invalidCredentials);
            return newSession(user);
        });
    }

    /**
     * Rotates a refresh token and revokes its session when a consumed token is reused.
     *
     * <p>Reuse revocation is committed before the authentication error is raised; otherwise rollback
     * would leave the compromised session active.
     *
     * @param raw the opaque refresh token supplied by the client
     * @return the new access and refresh tokens
     * @throws ApiException if the token or its session is no longer valid
     */
    public AuthResponse refresh(String raw) {
        var result = transactions.execute(status -> {
            String hash = Hashing.sha256(raw);
            var owner = refreshTokens.ownerByHash(hash).orElseThrow(AuthService::invalidCredentials);
            var user = users.lockById(owner).orElseThrow(AuthService::invalidCredentials);
            var token = refreshTokens.findByTokenHash(hash).orElseThrow(AuthService::invalidCredentials);
            var session = sessions.findById(token.getSessionId()).orElseThrow(AuthService::invalidCredentials);
            if (token.getConsumedAt() != null) {
                session.revoke(clock.instant());
                sessions.flush();
                return new RefreshResult(null);
            }
            if (!session.active(clock.instant()) || !token.getExpiresAt().isAfter(clock.instant())) {
                throw invalidCredentials();
            }
            token.consume(clock.instant());
            return new RefreshResult(issue(user, session));
        });
        if (result == null || result.response() == null) {
            throw invalidCredentials();
        }
        return result.response();
    }

    public void logout(String raw) {
        transactions.executeWithoutResult(status -> {
            String hash = Hashing.sha256(raw);
            refreshTokens.ownerByHash(hash).ifPresent(owner -> users.lockById(owner).ifPresent(user -> {
                refreshTokens.findByTokenHash(hash).flatMap(token -> sessions.findById(token.getSessionId()))
                        .ifPresent(session -> session.revoke(clock.instant()));
                sessions.flush();
            }));
        });
    }

    private AuthResponse newSession(User user) {
        return issue(user,
                sessions.saveAndFlush(new AuthSession(user.getId(), clock.instant().plus(properties.refreshTtl()))));
    }

    private AuthResponse issue(User user, AuthSession session) {
        String raw = tokens.refresh();
        refreshTokens.save(new RefreshToken(session.getId(), Hashing.sha256(raw), session.getExpiresAt()));
        long expiresIn = Math.min(properties.accessTtl().toSeconds(),
                Duration.between(clock.instant(), session.getExpiresAt()).toSeconds());
        return new AuthResponse(mapper.response(user), tokens.access(user, session), raw, "Bearer", expiresIn);
    }

    private record RefreshResult(AuthResponse response) {
    }

    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public static void validatePassword(String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.invalid("Пароль должен занимать не более 72 UTF-8 байт");
        }
    }

    private static ApiException invalidCredentials() {
        return new ApiException(401, "INVALID_CREDENTIALS", "Неверные данные входа или сессия недоступна");
    }
}
