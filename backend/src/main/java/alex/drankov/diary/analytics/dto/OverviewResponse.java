package alex.drankov.diary.analytics.dto;

import alex.drankov.diary.analytics.entity.Metric;

import java.util.List;
import java.util.Map;

public record OverviewResponse(
        PeriodResponse period,
        long dataRevision,
        CoverageResponse coverage,
        Map<Metric, List<SeriesPointResponse>> series,
        List<DailyValueResponse> dailyValues,
        List<TagFrequencyResponse> tagFrequencies,
        List<MedicationAdherenceResponse> medicationAdherence,
        List<HighAnxietyDayResponse> highAnxietyDays,
        List<ContextAmbiguityResponse> ambiguousContext,
        List<String> limitations
) {
}
