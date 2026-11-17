package alex.drankov.diary.users.dto;

import alex.drankov.diary.common.api.ApiException;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public final class UpdateProfileRequest {

    @Getter
    private String displayName;

    @Schema(hidden = true)
    private boolean displayNamePresent;

    @Getter
    private String timeZone;

    @Schema(hidden = true)
    private boolean timeZonePresent;

    @JsonSetter("displayName")
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.displayNamePresent = true;
    }

    public boolean hasDisplayName() {
        return displayNamePresent;
    }

    @JsonSetter("timeZone")
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
        this.timeZonePresent = true;
    }

    public boolean hasTimeZone() {
        return timeZonePresent;
    }

    public void validate() {
        if (!displayNamePresent && !timeZonePresent) {
            throw ApiException.invalid("Укажите поля изменения");
        }
        if (timeZonePresent && timeZone == null) {
            throw ApiException.invalid("Поле timeZone не может быть null");
        }
    }
}
