package alex.drankov.diary.entries.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import alex.drankov.diary.common.api.ApiException;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

import lombok.Getter;

public final class UpdateEntryRequest {

    @Getter
    private String text;

    @Schema(hidden = true)
    private boolean textPresent;

    @Getter
    @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    private OffsetDateTime occurredAt;

    @Schema(hidden = true)
    private boolean occurredAtPresent;

    @Getter
    private String timeZone;

    @Schema(hidden = true)
    private boolean timeZonePresent;

    @Getter
    private Boolean reportVisible;

    @Schema(hidden = true)
    private boolean reportVisiblePresent;

    @JsonSetter("text")
    public void setText(String text) {
        this.text = text;
        this.textPresent = true;
    }

    public boolean hasText() {
        return textPresent;
    }

    @JsonSetter("occurredAt")
    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
        this.occurredAtPresent = true;
    }

    public boolean hasOccurredAt() {
        return occurredAtPresent;
    }

    @JsonSetter("timeZone")
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
        this.timeZonePresent = true;
    }

    public boolean hasTimeZone() {
        return timeZonePresent;
    }

    @JsonSetter("reportVisible")
    public void setReportVisible(Boolean reportVisible) {
        this.reportVisible = reportVisible;
        this.reportVisiblePresent = true;
    }

    public boolean hasReportVisible() {
        return reportVisiblePresent;
    }

    public void validate() {
        if (!textPresent && !occurredAtPresent && !timeZonePresent && !reportVisiblePresent) {
            throw ApiException.invalid("Укажите поля изменения");
        }
        if (textPresent && text == null) {
            throw ApiException.invalid("Поле text не может быть null");
        }
        if (occurredAtPresent && occurredAt == null) {
            throw ApiException.invalid("Поле occurredAt не может быть null");
        }
        if (timeZonePresent && timeZone == null) {
            throw ApiException.invalid("Поле timeZone не может быть null");
        }
        if (reportVisiblePresent && reportVisible == null) {
            throw ApiException.invalid("Поле reportVisible не может быть null");
        }
    }
}
