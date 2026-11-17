package alex.drankov.diary.medications.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ScheduleVersionResponse(
        UUID id,
        UUID medicationId,
        Instant effectiveFrom,
        Instant effectiveUntil,
        LocalDate treatmentEndsOn,
        String timeZone,
        List<ScheduleSlotResponse> slots,
        Instant createdAt,
        long medicationVersion
) {
}
