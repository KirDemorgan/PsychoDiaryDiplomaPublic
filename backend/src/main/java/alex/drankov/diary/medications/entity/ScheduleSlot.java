package alex.drankov.diary.medications.entity;

import alex.drankov.diary.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "schedule_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleSlot extends BaseEntity {
    @Column(nullable = false)
    private UUID medicationId;

    @Column(nullable = false)
    private UUID scheduleVersionId;

    @Column(nullable = false)
    private LocalTime localTime;

    @Column(nullable = false, length = 100)
    private String days;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal dose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DoseUnit unit;

    public ScheduleSlot(
            UUID medicationId,
            UUID versionId,
            LocalTime time,
            Set<DayOfWeek> days,
            BigDecimal dose,
            DoseUnit unit
    ) {
        this.medicationId = medicationId;
        scheduleVersionId = versionId;
        localTime = time;
        this.days = days.stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
        this.dose = dose;
        this.unit = unit;
    }

    public Set<DayOfWeek> daysOfWeek() {
        return Arrays.stream(days.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
