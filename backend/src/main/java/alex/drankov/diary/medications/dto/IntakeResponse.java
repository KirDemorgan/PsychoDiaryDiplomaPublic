package alex.drankov.diary.medications.dto;

import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeKind;
import alex.drankov.diary.medications.entity.IntakeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IntakeResponse(
        UUID id,
        UUID medicationId,
        UUID scheduleSlotId,
        Instant plannedAt,
        IntakeKind kind,
        IntakeStatus status,
        Instant actualAt,
        BigDecimal actualDose,
        DoseUnit unit,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
