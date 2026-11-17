package alex.drankov.diary.reports.dto;

import alex.drankov.diary.medications.dto.IntakeResponse;
import alex.drankov.diary.medications.dto.OccurrenceResponse;
import alex.drankov.diary.medications.dto.ScheduleVersionResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportMedicationResponse(
        UUID id,
        String name,
        String form,
        Instant archivedAt,
        List<ScheduleVersionResponse> scheduleVersions,
        List<OccurrenceResponse> occurrences,
        List<IntakeResponse> intakes
) {
}
