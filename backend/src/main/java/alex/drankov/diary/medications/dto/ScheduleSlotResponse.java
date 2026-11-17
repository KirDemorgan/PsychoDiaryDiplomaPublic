package alex.drankov.diary.medications.dto;

import alex.drankov.diary.medications.entity.DoseUnit;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public record ScheduleSlotResponse(
        UUID id,
        LocalTime localTime,
        Set<DayOfWeek> daysOfWeek,
        BigDecimal dose,
        DoseUnit unit
) {
}
