package alex.drankov.diary.analytics.dto;

import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.entries.dto.EntrySummaryResponse;
import alex.drankov.diary.medications.dto.OccurrenceResponse;

import java.util.List;

public record DayResponse(
        CalendarDayResponse day,
        PageResponse<EntrySummaryResponse> entries,
        List<OccurrenceResponse> occurrences,
        List<ContextTagResponse> context,
        long dataRevision
) {
}
