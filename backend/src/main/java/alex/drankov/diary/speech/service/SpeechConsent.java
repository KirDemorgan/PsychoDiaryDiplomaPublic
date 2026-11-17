package alex.drankov.diary.speech.service;

import alex.drankov.diary.ai.service.AiConsent;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.speech.dto.SpeechConsentRequest;
import alex.drankov.diary.speech.dto.SpeechConsentResponse;
import alex.drankov.diary.speech.mapper.SpeechConsentMapper;
import alex.drankov.diary.users.repository.UserSettingsRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.time.Clock;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeechConsent {
    private final AiConsent ai;
    private final UserSettingsRepository settings;
    private final UserAccess access;
    private final SpeechConsentMapper mapper;
    private final Clock clock;

    @Value("${app.speech.consent-version:diary-speech-v1}")
    private String currentVersion;

    public boolean allowed(UUID user) {
        return ai.allowed(user) && settings.findByUserId(user).filter(value -> value.isSpeechEnabled()
                && currentVersion.equals(value.getSpeechConsentVersion()) && value.getSpeechConsentAcceptedAt() != null).isPresent();
    }

    public void require(UUID user) {
        ai.require(user);
        if (!allowed(user)) {
            throw new ApiException(403, "SPEECH_CONSENT_REQUIRED", "Примите описание обработки аудио и включите голосовой ввод");
        }
    }

    @Transactional(readOnly = true)
    public SpeechConsentResponse get(UUID user) {
        return mapper.response(settings.findByUserId(user).orElseThrow(ApiException::notFound), currentVersion);
    }

    @Transactional
    public SpeechConsentResponse update(UUID user, String ifMatch, SpeechConsentRequest request) {
        access.lock(user);
        var value = settings.findByUserId(user).orElseThrow(ApiException::notFound);
        Versions.require(ifMatch, value.getVersion());
        if (request.enabled() == null) {
            throw ApiException.invalid("Укажите enabled");
        }
        if (request.enabled()) {
            ai.require(user);
            if (!currentVersion.equals(request.consentVersion())) {
                throw new ApiException(403, "SPEECH_CONSENT_REQUIRED", "Примите актуальное описание обработки аудио");
            }
        }
        value.speech(request.enabled(), request.enabled() ? currentVersion : null,
                request.enabled() ? clock.instant() : null);
        settings.flush();
        return mapper.response(value, currentVersion);
    }
}
