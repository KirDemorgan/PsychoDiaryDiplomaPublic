package alex.drankov.diary.reports.service.model;

import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.medications.dto.OccurrenceResponse;
import alex.drankov.diary.medications.entity.IntakeEvent;
import alex.drankov.diary.medications.entity.Medication;
import alex.drankov.diary.ratings.entity.StateRating;
import alex.drankov.diary.reports.dto.CreateReportRequest;
import alex.drankov.diary.reports.dto.ReportMedicationResponse;
import alex.drankov.diary.tags.entity.EntryTag;

import java.util.List;

public record ReportSourceData(
        long dataRevision,
        CreateReportRequest selection,
        List<StateRating> ratings,
        List<DiaryEntry> entries,
        List<EntryTag> tags,
        List<Medication> medications,
        List<OccurrenceResponse> occurrences,
        List<IntakeEvent> intakes,
        List<ReportMedicationResponse> medicationDetails
) {
}
