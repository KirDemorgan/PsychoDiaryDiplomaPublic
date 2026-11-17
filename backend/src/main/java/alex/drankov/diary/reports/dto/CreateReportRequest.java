package alex.drankov.diary.reports.dto;

import alex.drankov.diary.reports.entity.EntrySelection;
import alex.drankov.diary.reports.entity.MedicationSelection;
import alex.drankov.diary.reports.entity.ReportSection;
import alex.drankov.diary.tags.entity.TagCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateReportRequest(
        LocalDate from,
        LocalDate to,
        String timeZone,
        String displayName,
        Set<ReportSection> sections,
        EntrySelection entrySelection,
        List<UUID> entryIds,
        MedicationSelection medicationSelection,
        List<UUID> medicationIds,
        List<TagCode> comparisonTagCodes
) {
}
