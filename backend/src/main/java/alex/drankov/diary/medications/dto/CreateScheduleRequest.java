package alex.drankov.diary.medications.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateScheduleRequest(
        OffsetDateTime effectiveFrom,
        LocalDate treatmentEndsOn,
        String timeZone,
        List<ScheduleSlotRequest> slots
) {
}
