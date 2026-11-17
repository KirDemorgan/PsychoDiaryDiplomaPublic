package alex.drankov.diary.analytics.dto;



public record MedicationDayResponse(
        long planned,
        long taken,
        long skipped,
        long noData
) {
}
