package alex.drankov.diary.medications.dto;

import alex.drankov.diary.medications.entity.DoseUnit;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record ScheduleSlotRequest(
        LocalTime localTime,
        Set<DayOfWeek> daysOfWeek,
        BigDecimal dose,
        DoseUnit unit
) {
}
