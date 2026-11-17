package alex.drankov.diary.medications.dto;

import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeKind;
import alex.drankov.diary.medications.entity.IntakeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreateIntakeRequest(
        IntakeKind kind,
        String occurrenceId,
        UUID medicationId,
        IntakeStatus status,
        Instant actualAt,
        BigDecimal actualDose,
        DoseUnit unit
) {
}
