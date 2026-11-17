package alex.drankov.diary.medications.entity;

import alex.drankov.diary.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schedule_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleVersion extends BaseEntity {
    @Column(nullable = false)
    private UUID medicationId;

    @Column(nullable = false)
    private Instant effectiveFrom;

    @Column
    private Instant effectiveUntil;

    @Column
    private LocalDate treatmentEndsOn;

    @Column(nullable = false, length = 100)
    private String timeZone;

    public ScheduleVersion(
            UUID medicationId,
            Instant from,
            LocalDate endsOn,
            String timeZone
    ) {
        this.medicationId = medicationId;
        effectiveFrom = from;
        treatmentEndsOn = endsOn;
        this.timeZone = timeZone;
    }

    public void close(Instant until) {
        effectiveUntil = until;
    }
}
