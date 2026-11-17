package alex.drankov.diary.analytics.dto;

import alex.drankov.diary.analytics.entity.Metric;

import java.util.Map;

public record CoverageResponse(
        Map<Metric, MetricCoverageResponse> metrics,
        long filledSlots,
        long expectedSlots,
        long filledDays,
        long totalDays
) {
}
