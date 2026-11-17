package alex.drankov.diary.users.service;

import alex.drankov.diary.common.api.Texts;
import alex.drankov.diary.users.dto.UpdateProfileRequest;
import alex.drankov.diary.users.dto.UpdateSettingsRequest;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.TimeRules;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.users.dto.UserResponse;
import alex.drankov.diary.users.dto.UserSettingsResponse;
import alex.drankov.diary.users.entity.UserSettings;
import alex.drankov.diary.users.mapper.UserMapper;
import alex.drankov.diary.users.mapper.UserSettingsMapper;
import alex.drankov.diary.users.repository.UserRepository;
import alex.drankov.diary.users.repository.UserSettingsRepository;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {
    private final UserAccess access;
    private final UserRepository users;
    private final UserMapper mapper;
    private final UserSettingsRepository settings;
    private final UserSettingsMapper settingsMapper;
    private final Clock clock;

    @Value("${app.ai-consent-version}")
    private String currentConsent;

    public UserResponse get(UUID id) {
        return mapper.response(access.read(id));
    }

    public UserSettingsResponse settings(UUID id) {
        return settingsMapper.response(settingsFor(id));
    }

    @Transactional
    public UserResponse patch(UUID id, String ifMatch, UpdateProfileRequest request) {
        var user = access.lock(id);
        Versions.require(ifMatch, user.getVersion());
        request.validate();
        String name = request.hasDisplayName() ? request.getDisplayName() : user.getDisplayName();
        String zone = request.hasTimeZone() ? request.getTimeZone() : user.getTimeZone();
        validateName(name);
        TimeRules.zone(zone);
        user.changeProfile(name, zone);
        user.advanceDataRevision();
        users.flush();
        return mapper.response(user);
    }

    @Transactional
    public UserSettingsResponse patchSettings(UUID id, String ifMatch, UpdateSettingsRequest request) {
        var user = access.lock(id);
        var value = settingsFor(id);
        Versions.require(ifMatch, value.getVersion());
        request.validate();
        if (request.hasAiConsentVersion()) {
            String consent = request.getAiConsentVersion();
            if (!currentConsent.equals(consent)) {
                throw ApiException.invalid("Примите актуальную версию описания AI");
            }
            value.consent(consent, clock.instant());
        }
        if (request.hasAiEnabled()) {
            boolean enabled = request.getAiEnabled();
            if (enabled && !currentConsent.equals(value.getAiConsentVersion())) {
                throw new ApiException(403, "AI_CONSENT_REQUIRED", "Требуется согласие на обработку AI");
            }
            value.ai(enabled);
        }
        if (request.hasRemindersEnabled()) {
            value.reminders(request.getRemindersEnabled());
        }
        if (request.hasNeutralNotificationText()) {
            value.neutral(request.getNeutralNotificationText());
        }
        if (request.hasReminderRepeatMinutes()) {
            value.repeat(request.getReminderRepeatMinutes());
        }
        if (request.hasTheme()) {
            String theme = request.getTheme();
            if (!Set.of("SYSTEM", "LIGHT", "DARK").contains(theme)) {
                throw ApiException.invalid("Неизвестная тема");
            }
            value.theme(theme);
        }
        user.advanceDataRevision();
        settings.flush();
        return settingsMapper.response(value);
    }

    private UserSettings settingsFor(UUID id) {
        return settings.findByUserId(id).orElseThrow(ApiException::notFound);
    }

    public static void validateName(String name) {
        if (name != null && (name.isBlank() || Texts.exceeds(name, 100))) {
            throw ApiException.invalid("Имя должно содержать от 1 до 100 символов");
        }
    }
}
