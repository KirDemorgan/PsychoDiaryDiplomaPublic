package alex.drankov.diary.medications.dto;

import java.time.Instant;
import java.util.UUID;

public record MedicationResponse(
        UUID id,
        String name,
        String form,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        ScheduleVersionResponse currentSchedule
) {
}
