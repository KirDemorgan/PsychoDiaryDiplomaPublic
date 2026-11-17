package alex.drankov.diary.auth.security;

import alex.drankov.diary.common.api.ApiException;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static alex.drankov.diary.TestFixtures.CLOCK;
import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.security;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthThrottleTest {
    @Test
    @DisplayName("Лимит входов блокирует повторы и снимается после окончания окна")
    void boundsRepeatedLoginAndReleasesAfterWindow() {
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        var throttle = new AuthThrottle(security(), clock);
        for (int i = 0; i < 20; i++) {
            throttle.acquire("127.0.0.1", "demo@example.test");
        }
        assertThatThrownBy(() -> throttle.acquire("127.0.0.1", "demo@example.test"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(429));
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(16)));
        throttle.acquire("127.0.0.1", "demo@example.test");
    }

    @Test
    @DisplayName("Смена IP не обходит лимит входов по email")
    void emailLimitSurvivesIpChanges() {
        var throttle = new AuthThrottle(security(), CLOCK);
        for (int i = 0; i < 20; i++) {
            throttle.acquire("ip-" + i, "demo@example.test");
        }
        assertThatThrownBy(() -> throttle.acquire("another", "DEMO@example.test")).isInstanceOf(ApiException.class);
    }
}
