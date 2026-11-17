package alex.drankov.diary.analytics.dto;



public record MetricCoverageResponse(
        long filled,
        long expected,
        long filledDays,
        long totalDays
) {
}
