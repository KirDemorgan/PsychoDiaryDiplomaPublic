package alex.drankov.diary;

import alex.drankov.diary.common.persistence.BaseEntity;
import alex.drankov.diary.security.SecurityProperties;
import alex.drankov.diary.users.entity.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.springframework.test.util.ReflectionTestUtils;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    public static <T extends BaseEntity> T persisted(T value) {
        ReflectionTestUtils.setField(value, "version", 0L);
        ReflectionTestUtils.setField(value, "createdAt", NOW);
        ReflectionTestUtils.setField(value, "updatedAt", NOW);
        return value;
    }

    public static User user() {
        return persisted(new User("demo@example.test", "hash", null, "Asia/Yekaterinburg"));
    }

    public static SecurityProperties security() {
        return new SecurityProperties("https://diary.local", "diary-mobile", Base64.getEncoder().encodeToString(new byte[32]),
                Duration.ofMinutes(15), Duration.ofDays(30), java.util.List.of("http://localhost:8081"), Duration.ofMinutes(15), 20);
    }
}
