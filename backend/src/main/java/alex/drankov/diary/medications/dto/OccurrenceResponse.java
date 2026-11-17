package alex.drankov.diary.medications.dto;

import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OccurrenceResponse(
        String occurrenceId,
        UUID medicationId,
        String medicationName,
        UUID scheduleVersionId,
        UUID scheduleSlotId,
        Instant plannedAt,
        BigDecimal plannedDose,
        DoseUnit unit,
        String timeZone,
        IntakeStatus status,
        IntakeResponse intake
) {
}
