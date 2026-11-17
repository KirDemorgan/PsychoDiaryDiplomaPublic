package alex.drankov.diary.ai.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.users.entity.UserSettings;
import alex.drankov.diary.users.repository.UserSettingsRepository;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static alex.drankov.diary.TestFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiConsentTest {
    @Test
    @DisplayName("AI требует включение, актуальную версию согласия и дату принятия; отсутствие владельца даёт 404")
    void consentRequirements() {
        var user = UUID.randomUUID();
        var repository = mock(UserSettingsRepository.class);
        var consent = new AiConsent(repository);
        ReflectionTestUtils.setField(consent, "currentVersion", "v1");
        var settings = new UserSettings(user);
        when(repository.findByUserId(user)).thenReturn(Optional.of(settings));
        assertThat(consent.allowed(user)).isFalse();
        assertThatThrownBy(() -> consent.require(user)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("AI_DISABLED"));
        settings.ai(true);
        settings.consent("old", NOW);
        assertThat(consent.allowed(user)).isFalse();
        assertThatThrownBy(() -> consent.require(user)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("AI_CONSENT_REQUIRED"));
        settings.consent("v1", null);
        assertThat(consent.allowed(user)).isFalse();
        settings.consent("v1", NOW);
        assertThat(consent.allowed(user)).isTrue();
        consent.require(user);
        when(repository.findByUserId(user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> consent.require(user)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(404));
    }
}
