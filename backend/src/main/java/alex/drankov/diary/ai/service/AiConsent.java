package alex.drankov.diary.ai.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.users.repository.UserSettingsRepository;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiConsent {
    private final UserSettingsRepository settings;

    @Value("${app.ai-consent-version}")
    private String currentVersion;

    public boolean allowed(UUID user) {
        return settings.findByUserId(user).filter(value -> value.isAiEnabled()
                && currentVersion.equals(value.getAiConsentVersion()) && value.getAiConsentAcceptedAt() != null).isPresent();
    }

    public void require(UUID user) {
        var value = settings.findByUserId(user).orElseThrow(ApiException::notFound);
        if (!value.isAiEnabled()) {
            throw new ApiException(403, "AI_DISABLED", "AI отключён в настройках");
        }
        if (!currentVersion.equals(value.getAiConsentVersion()) || value.getAiConsentAcceptedAt() == null) {
            throw new ApiException(403, "AI_CONSENT_REQUIRED", "Требуется актуальное согласие на AI");
        }
    }
}
