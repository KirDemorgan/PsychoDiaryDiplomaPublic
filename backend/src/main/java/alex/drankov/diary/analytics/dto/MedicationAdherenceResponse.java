package alex.drankov.diary.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MedicationAdherenceResponse(
        UUID medicationId,
        String medicationName,
        long planned,
        long taken,
        long skipped,
        long noData,
        BigDecimal takenRate,
        BigDecimal markedCoverage,
        long unplanned
) {
}
