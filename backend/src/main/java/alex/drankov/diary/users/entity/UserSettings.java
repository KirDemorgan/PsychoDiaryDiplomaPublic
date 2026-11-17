package alex.drankov.diary.users.entity;

import alex.drankov.diary.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSettings extends BaseEntity {
    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private boolean aiEnabled;
    private String aiConsentVersion;
    private Instant aiConsentAcceptedAt;

    @Column(nullable = false)
    private boolean speechEnabled;
    private String speechConsentVersion;
    private Instant speechConsentAcceptedAt;

    @Column(nullable = false)
    private boolean remindersEnabled = true;
    private Integer reminderRepeatMinutes;

    @Column(nullable = false)
    private boolean neutralNotificationText = true;

    @Column(nullable = false)
    private String theme = "SYSTEM";

    public UserSettings(UUID userId) {
        this.userId = userId;
    }

    public void consent(String version, Instant at) {
        aiConsentVersion = version;
        aiConsentAcceptedAt = at;
    }

    public void ai(boolean value) {
        aiEnabled = value;
    }

    public void speech(boolean enabled, String version, Instant at) {
        speechEnabled = enabled;
        speechConsentVersion = version;
        speechConsentAcceptedAt = at;
    }

    public void reminders(boolean value) {
        remindersEnabled = value;
    }

    public void repeat(Integer value) {
        reminderRepeatMinutes = value;
    }

    public void neutral(boolean value) {
        neutralNotificationText = value;
    }

    public void theme(String value) {
        theme = value;
    }
}
