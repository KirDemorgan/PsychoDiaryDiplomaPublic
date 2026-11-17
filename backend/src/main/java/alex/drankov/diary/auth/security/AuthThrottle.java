package alex.drankov.diary.auth.security;

import alex.drankov.diary.auth.service.AuthService;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.crypto.Hashing;
import alex.drankov.diary.security.SecurityProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bounded in-memory limit for a single-instance local API. No emails/IPs are logged.
 */
@Component
@RequiredArgsConstructor
public class AuthThrottle {
    private final SecurityProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public synchronized void acquire(String ip, String email) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(e -> !e.getValue().until().isAfter(now));
        if (windows.size() >= 10000) {
            throw limited();
        }
        hit(Hashing.sha256(properties.secretBase64() + ":ip:" + ip), now);
        hit(Hashing.sha256(properties.secretBase64() + ":email:" + AuthService.normalize(email)), now);
    }

    private void hit(String key, Instant now) {
        var current = windows.getOrDefault(key, new Window(now.plus(properties.authWindow()), 0));
        if (current.count() >= properties.authMaxAttempts()) {
            throw limited();
        }
        windows.put(key, new Window(current.until(), current.count() + 1));
    }

    private record Window(Instant until, int count) {
    }

    private static ApiException limited() {
        return new ApiException(429, "AUTH_RATE_LIMIT", "Повторите вход позже");
    }
}
