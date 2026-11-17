package alex.drankov.diary.analytics.dto;

import java.time.LocalDate;

public record PeriodResponse(
        LocalDate from,
        LocalDate to,
        String timeZone
) {
}
