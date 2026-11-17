package alex.drankov.diary.users.service;

import alex.drankov.diary.users.dto.UpdateProfileRequest;
import alex.drankov.diary.users.dto.UpdateSettingsRequest;
import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.entity.UserSettings;
import alex.drankov.diary.users.mapper.UserMapper;
import alex.drankov.diary.users.mapper.UserSettingsMapper;
import alex.drankov.diary.users.repository.UserRepository;
import alex.drankov.diary.users.repository.UserSettingsRepository;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static alex.drankov.diary.TestFixtures.CLOCK;
import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock
    UserAccess access;
    @Mock
    UserRepository users;
    @Mock
    UserSettingsRepository repository;
    ProfileService service;
    User user;
    UserSettings settings;
    JsonMapper json = new JsonMapper();

    @BeforeEach
    void setup() {
        user = user();
        settings = persisted(new UserSettings(user.getId()));
        service = new ProfileService(access, users, Mappers.getMapper(UserMapper.class), repository,
                Mappers.getMapper(UserSettingsMapper.class), CLOCK);
        ReflectionTestUtils.setField(service, "currentConsent", "diary-ai-v1");
    }

    @Test
    @DisplayName("PATCH профиля различает отсутствие поля и явное очищение имени")
    void profileMissingAndNullHaveDifferentMeaning() {
        when(access.lock(user.getId())).thenReturn(user);
        service.patch(user.getId(), "\"v0\"", json.readValue("{\"displayName\":\"Demo\"}", UpdateProfileRequest.class));
        assertThat(user.getTimeZone()).isEqualTo("Asia/Yekaterinburg");
        assertThat(user.getDisplayName()).isEqualTo("Demo");
        service.patch(user.getId(), "\"v0\"",
                json.readValue("{\"displayName\":null,\"timeZone\":\"UTC\"}", UpdateProfileRequest.class));
        assertThat(user.getDisplayName()).isNull();
        assertThat(user.getTimeZone()).isEqualTo("UTC");
        assertThatThrownBy(() -> service.patch(user.getId(), "\"v0\"",
                json.readValue("{\"timeZone\":null}", UpdateProfileRequest.class))).isInstanceOf(ApiException.class);
        when(access.read(user.getId())).thenReturn(user);
        assertThat(service.get(user.getId()).email()).isEqualTo("demo@example.test");
    }

    @Test
    @DisplayName("Включение AI требует актуального согласия с серверным временем подтверждения")
    void aiRequiresCurrentConsentAndConsentTimeComesFromServer() {
        when(access.lock(user.getId())).thenReturn(user);
        when(repository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
        assertThatThrownBy(() -> service.patchSettings(user.getId(), "\"v0\"",
                json.readValue("{\"aiEnabled\":true}", UpdateSettingsRequest.class)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
        assertThatThrownBy(() -> service.patchSettings(user.getId(), "\"v0\"",
                json.readValue("{\"aiConsentVersion\":\"old\"}", UpdateSettingsRequest.class))).isInstanceOf(
                ApiException.class);
        var response = service.patchSettings(user.getId(), "\"v0\"",
                json.readValue("{\"aiEnabled\":true,\"aiConsentVersion\":\"diary-ai-v1\"}",
                        UpdateSettingsRequest.class));
        assertThat(response.aiEnabled()).isTrue();
        assertThat(response.aiConsentAcceptedAt()).isEqualTo(NOW);
        service.patchSettings(user.getId(), "\"v0\"",
                json.readValue("{\"aiEnabled\":false}", UpdateSettingsRequest.class));
        assertThat(settings.isAiEnabled()).isFalse();
    }

    @Test
    @DisplayName("Настройки проверяют перечисления и допускают очистку интервала напоминаний")
    void settingsValidateEnumsAndNullableRepeat() {
        when(access.lock(user.getId())).thenReturn(user);
        when(repository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
        service.patchSettings(user.getId(), "\"v0\"", json.readValue(
                "{\"remindersEnabled\":false,\"neutralNotificationText\":false,\"reminderRepeatMinutes\":5,\"theme\":\"DARK\"}",
                UpdateSettingsRequest.class));
        assertThat(settings.isRemindersEnabled()).isFalse();
        assertThat(settings.isNeutralNotificationText()).isFalse();
        assertThat(settings.getReminderRepeatMinutes()).isEqualTo(5);
        assertThat(settings.getTheme()).isEqualTo("DARK");
        service.patchSettings(user.getId(), "\"v0\"",
                json.readValue("{\"reminderRepeatMinutes\":null}", UpdateSettingsRequest.class));
        assertThat(settings.getReminderRepeatMinutes()).isNull();
        assertThat(service.settings(user.getId()).theme()).isEqualTo("DARK");
        assertThatThrownBy(() -> service.patchSettings(user.getId(), "\"v0\"",
                json.readValue("{\"theme\":\"UNKNOWN\"}", UpdateSettingsRequest.class))).isInstanceOf(
                ApiException.class);
        assertThatThrownBy(() -> service.patchSettings(user.getId(), "\"v9\"",
                json.readValue("{\"theme\":\"LIGHT\"}", UpdateSettingsRequest.class))).isInstanceOf(ApiException.class);
    }
}
