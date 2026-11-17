package alex.drankov.diary.entries.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateEntryRequest(
        @NotBlank
        String text,
        @NotNull
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime occurredAt,
        @NotBlank
        String timeZone,
        Boolean reportVisible
) {
    @Override
    public String toString() {
        return "EntryCreate[redacted]";
    }
}
