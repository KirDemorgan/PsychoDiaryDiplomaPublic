package alex.drankov.diary.users.dto;

import alex.drankov.diary.common.api.ApiException;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public final class UpdateSettingsRequest {

    @Getter
    private Boolean aiEnabled;

    @Schema(hidden = true)
    private boolean aiEnabledPresent;

    @Getter
    private String aiConsentVersion;

    @Schema(hidden = true)
    private boolean aiConsentVersionPresent;

    @Getter
    private Boolean remindersEnabled;

    @Schema(hidden = true)
    private boolean remindersEnabledPresent;

    @Getter
    private Integer reminderRepeatMinutes;

    @Schema(hidden = true)
    private boolean reminderRepeatMinutesPresent;

    @Getter
    private Boolean neutralNotificationText;

    @Schema(hidden = true)
    private boolean neutralNotificationTextPresent;

    @Getter
    private String theme;

    @Schema(hidden = true)
    private boolean themePresent;

    @JsonSetter("aiEnabled")
    public void setAiEnabled(Boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
        this.aiEnabledPresent = true;
    }

    public boolean hasAiEnabled() {
        return aiEnabledPresent;
    }

    @JsonSetter("aiConsentVersion")
    public void setAiConsentVersion(String aiConsentVersion) {
        this.aiConsentVersion = aiConsentVersion;
        this.aiConsentVersionPresent = true;
    }

    public boolean hasAiConsentVersion() {
        return aiConsentVersionPresent;
    }

    @JsonSetter("remindersEnabled")
    public void setRemindersEnabled(Boolean remindersEnabled) {
        this.remindersEnabled = remindersEnabled;
        this.remindersEnabledPresent = true;
    }

    public boolean hasRemindersEnabled() {
        return remindersEnabledPresent;
    }

    @JsonSetter("reminderRepeatMinutes")
    public void setReminderRepeatMinutes(Integer reminderRepeatMinutes) {
        this.reminderRepeatMinutes = reminderRepeatMinutes;
        this.reminderRepeatMinutesPresent = true;
    }

    public boolean hasReminderRepeatMinutes() {
        return reminderRepeatMinutesPresent;
    }

    @JsonSetter("neutralNotificationText")
    public void setNeutralNotificationText(Boolean neutralNotificationText) {
        this.neutralNotificationText = neutralNotificationText;
        this.neutralNotificationTextPresent = true;
    }

    public boolean hasNeutralNotificationText() {
        return neutralNotificationTextPresent;
    }

    @JsonSetter("theme")
    public void setTheme(String theme) {
        this.theme = theme;
        this.themePresent = true;
    }

    public boolean hasTheme() {
        return themePresent;
    }

    public void validate() {
        if (!aiEnabledPresent && !aiConsentVersionPresent && !remindersEnabledPresent && !reminderRepeatMinutesPresent && !neutralNotificationTextPresent && !themePresent) {
            throw ApiException.invalid("Укажите поля изменения");
        }
        if (aiEnabledPresent && aiEnabled == null) {
            throw ApiException.invalid("Поле aiEnabled не может быть null");
        }
        if (aiConsentVersionPresent && aiConsentVersion == null) {
            throw ApiException.invalid("Поле aiConsentVersion не может быть null");
        }
        if (remindersEnabledPresent && remindersEnabled == null) {
            throw ApiException.invalid("Поле remindersEnabled не может быть null");
        }
        if (neutralNotificationTextPresent && neutralNotificationText == null) {
            throw ApiException.invalid("Поле neutralNotificationText не может быть null");
        }
        if (themePresent && theme == null) {
            throw ApiException.invalid("Поле theme не может быть null");
        }
        if (reminderRepeatMinutes != null && (reminderRepeatMinutes < 1 || reminderRepeatMinutes > 1440)) {
            throw ApiException.invalid("Интервал повторения должен быть от 1 до 1440 минут");
        }
    }
}
