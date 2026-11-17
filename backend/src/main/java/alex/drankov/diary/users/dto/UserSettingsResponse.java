package alex.drankov.diary.users.dto;

import java.time.Instant;

public record UserSettingsResponse(
        boolean aiEnabled,
        String aiConsentVersion,
        Instant aiConsentAcceptedAt,
        boolean remindersEnabled,
        Integer reminderRepeatMinutes,
        boolean neutralNotificationText,
        String theme,
        long version
) {
}
