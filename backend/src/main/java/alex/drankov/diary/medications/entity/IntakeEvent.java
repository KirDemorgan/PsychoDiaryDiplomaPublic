package alex.drankov.diary.medications.entity;

import alex.drankov.diary.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "intake_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntakeEvent extends BaseEntity {
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID medicationId;

    @Column
    private UUID scheduleSlotId;

    @Column
    private Instant plannedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntakeKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntakeStatus status;

    @Column
    private Instant actualAt;

    @Column(precision = 14, scale = 4)
    private BigDecimal actualDose;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DoseUnit unit;

    public IntakeEvent(
            UUID userId,
            UUID medicationId,
            UUID slotId,
            Instant plannedAt,
            IntakeKind kind
    ) {
        this.userId = userId;
        this.medicationId = medicationId;
        scheduleSlotId = slotId;
        this.plannedAt = plannedAt;
        this.kind = kind;
    }

    public void mark(
            IntakeStatus status,
            Instant actualAt,
            BigDecimal dose,
            DoseUnit unit
    ) {
        this.status = status;
        this.actualAt = actualAt;
        actualDose = dose;
        this.unit = unit;
    }
}
