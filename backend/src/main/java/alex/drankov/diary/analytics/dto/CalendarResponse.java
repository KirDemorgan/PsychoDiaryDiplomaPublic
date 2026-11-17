package alex.drankov.diary.analytics.dto;

import java.util.List;

public record CalendarResponse(
        PeriodResponse period,
        List<CalendarDayResponse> days,
        long dataRevision
) {
}
