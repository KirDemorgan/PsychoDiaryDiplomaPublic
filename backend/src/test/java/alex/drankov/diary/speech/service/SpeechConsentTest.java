package alex.drankov.diary.speech.service;

import alex.drankov.diary.TestFixtures;
import alex.drankov.diary.ai.service.AiConsent;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.speech.dto.SpeechConsentRequest;
import alex.drankov.diary.speech.mapper.SpeechConsentMapper;
import alex.drankov.diary.users.entity.UserSettings;
import alex.drankov.diary.users.repository.UserSettingsRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeechConsentTest {
    private final AiConsent ai = mock(AiConsent.class);
    private final UserSettingsRepository settings = mock(UserSettingsRepository.class);
    private final UserAccess access = mock(UserAccess.class);
    private final UUID user = UUID.randomUUID();
    private final UserSettings value = TestFixtures.persisted(new UserSettings(user));
    private final SpeechConsent consent = new SpeechConsent(ai, settings, access,
            Mappers.getMapper(SpeechConsentMapper.class), TestFixtures.CLOCK);

    private void setup() {
        ReflectionTestUtils.setField(consent, "currentVersion", "diary-speech-v1");
        when(settings.findByUserId(user)).thenReturn(Optional.of(value));
        when(ai.allowed(user)).thenReturn(true);
    }

    @Test
    @DisplayName("Принятие текстового AI не включает отправку аудио; требуется отдельное согласие")
    void separateConsent() {
        setup();
        assertThat(consent.allowed(user)).isFalse();
        assertThatThrownBy(() -> consent.require(user)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("SPEECH_CONSENT_REQUIRED"));
        assertThatThrownBy(() -> consent.update(user, "\"v0\"", new SpeechConsentRequest(true, "old")))
                .isInstanceOf(ApiException.class);
        var response = consent.update(user, "\"v0\"", new SpeechConsentRequest(true, "diary-speech-v1"));
        assertThat(response.enabled()).isTrue();
        assertThat(response.acceptedAt()).isEqualTo(TestFixtures.NOW);
        assertThat(consent.allowed(user)).isTrue();
        consent.require(user);
        verify(access, org.mockito.Mockito.times(2)).lock(user);
    }

    @Test
    @DisplayName("Отключение голоса стирает его согласие; общий AI выключатель также запрещает расшифровку")
    void disable() {
        setup();
        value.speech(true, "diary-speech-v1", TestFixtures.NOW);
        assertThat(consent.get(user).currentVersion()).isEqualTo("diary-speech-v1");
        when(ai.allowed(user)).thenReturn(false);
        assertThat(consent.allowed(user)).isFalse();
        consent.update(user, "\"v0\"", new SpeechConsentRequest(false, null));
        assertThat(value.getSpeechConsentVersion()).isNull();
        assertThat(value.getSpeechConsentAcceptedAt()).isNull();
        assertThat(value.isSpeechEnabled()).isFalse();
    }

    @Test
    @DisplayName("Устаревшая версия настроек и пустой enabled отклоняются")
    void invalidUpdate() {
        setup();
        assertThatThrownBy(() -> consent.update(user, "\"v1\"", new SpeechConsentRequest(true, "diary-speech-v1")))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getStatus()).isEqualTo(412));
        assertThatThrownBy(() -> consent.update(user, "\"v0\"", new SpeechConsentRequest(null, null)))
                .isInstanceOf(ApiException.class);
    }
}
