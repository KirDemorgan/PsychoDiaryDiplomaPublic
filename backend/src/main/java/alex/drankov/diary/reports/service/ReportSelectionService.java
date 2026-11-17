package alex.drankov.diary.reports.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.TimeRules;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.entries.repository.EntryRepository;
import alex.drankov.diary.medications.entity.Medication;
import alex.drankov.diary.medications.mapper.MedicationMapper;
import alex.drankov.diary.medications.repository.IntakeRepository;
import alex.drankov.diary.medications.repository.MedicationRepository;
import alex.drankov.diary.medications.service.IntakeService;
import alex.drankov.diary.medications.service.MedicationService;
import alex.drankov.diary.ratings.repository.RatingRepository;
import alex.drankov.diary.reports.dto.CreateReportRequest;
import alex.drankov.diary.reports.dto.ReportMedicationResponse;
import alex.drankov.diary.reports.entity.EntrySelection;
import alex.drankov.diary.reports.entity.MedicationSelection;
import alex.drankov.diary.reports.entity.ReportSection;
import alex.drankov.diary.reports.service.model.ReportSourceData;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.repository.EntryTagRepository;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.service.ProfileService;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportSelectionService {
    private final EntryRepository entries;
    private final RatingRepository ratings;
    private final EntryTagRepository tags;
    private final MedicationRepository medications;
    private final MedicationService medicationService;
    private final IntakeRepository intakes;
    private final IntakeService intakeService;
    private final MedicationMapper medicationMapper;

    public ReportSourceData select(
            User owner,
            CreateReportRequest request
    ) {
        TimeRules.range(request.from(), request.to(), 366);
        var zone = TimeRules.zone(request.timeZone() == null ? owner.getTimeZone() : request.timeZone());
        String name = request.displayName() == null ? owner.getDisplayName() : request.displayName();
        ProfileService.validateName(name);
        if (request.sections() == null || request.sections().isEmpty() || request.sections().stream().anyMatch(Objects::isNull)
                || request.entrySelection() == null || request.medicationSelection() == null) {
            throw ApiException.invalid("Укажите разделы и правила выбора источников");
        }
        var entryIds = ids(request.entryIds());
        var medicationIds = ids(request.medicationIds());
        var codes = request.comparisonTagCodes() == null ? List.<TagCode>of() : request.comparisonTagCodes();
        if (codes.size() > 12 || codes.stream().anyMatch(Objects::isNull) || codes.stream().distinct().count() != codes.size()) {
            throw ApiException.invalid("Укажите до 12 неповторяющихся кодов сравнения");
        }
        if (request.entrySelection() != EntrySelection.EXPLICIT && !entryIds.isEmpty()
                || request.medicationSelection() != MedicationSelection.EXPLICIT && !medicationIds.isEmpty()) {
            throw ApiException.invalid("Список идентификаторов используется только с EXPLICIT");
        }
        var selection = new CreateReportRequest(request.from(), request.to(), zone.getId(), name,
                Collections.unmodifiableSet(EnumSet.copyOf(request.sections())), request.entrySelection(), entryIds, request.medicationSelection(), medicationIds,
                codes.stream().sorted().toList());
        Instant from = selection.from().atStartOfDay(zone).toInstant();
        Instant until = selection.to().plusDays(1).atStartOfDay(zone).toInstant();
        List<DiaryEntry> notes = switch (selection.entrySelection()) {
            case NONE -> List.of();
            case ALL_VISIBLE -> entries.inPeriod(owner.getId(), from, until).stream().filter(DiaryEntry::isReportVisible).toList();
            case EXPLICIT -> entryIds.stream().map(id -> {
                var entry = entries.findByIdAndUserId(id, owner.getId()).orElseThrow(ApiException::notFound);
                if (!entry.isReportVisible()) {
                    throw new ApiException(409, "SOURCE_NOT_REPORTABLE", "Запись исключена из отчётов");
                }
                if (entry.getOccurredAt().isBefore(from) || !entry.getOccurredAt().isBefore(until)) {
                    throw ApiException.invalid("Выбранная запись не входит в период");
                }
                return entry;
            }).toList();
        };
        if (notes.size() > 3000) {
            throw ApiException.invalid("Для PDF выберите не более 3000 записей или сократите период");
        }
        List<Medication> selected = switch (selection.medicationSelection()) {
            case NONE -> List.of();
            case ALL -> medications.findByUserIdOrderByCreatedAtAscIdAsc(owner.getId());
            case EXPLICIT -> medicationIds.stream().map(id -> medicationService.find(owner.getId(), id)).toList();
        };
        var noteSet = notes.stream().map(DiaryEntry::getId).collect(Collectors.toSet());
        var medicationSet = selected.stream().map(Medication::getId).collect(Collectors.toSet());
        var selectedTags = tags.currentForUser(owner.getId()).stream().filter(tag -> noteSet.contains(tag.getEntryId())).toList();
        var plan = selected.isEmpty() ? List.<alex.drankov.diary.medications.dto.OccurrenceResponse>of()
                : intakeService.occurrences(owner.getId(), selection.from(), selection.to(), zone.getId(), null).items().stream()
                .filter(value -> medicationSet.contains(value.medicationId())).toList();
        var marks = intakes.findByUserIdOrderByCreatedAtAscIdAsc(owner.getId()).stream()
                .filter(value -> medicationSet.contains(value.getMedicationId()))
                .filter(value -> {
                    Instant at = value.getPlannedAt() == null ? value.getActualAt() : value.getPlannedAt();
                    return !at.isBefore(from) && at.isBefore(until);
                }).toList();
        var details = selected.stream().map(medication -> new ReportMedicationResponse(medication.getId(), medication.getName(),
                medication.getForm(), medication.getArchivedAt(), medicationService.history(owner.getId(), medication.getId()).stream()
                .filter(version -> version.effectiveFrom().isBefore(until)
                        && (version.effectiveUntil() == null || version.effectiveUntil().isAfter(from))).toList(),
                plan.stream().filter(value -> value.medicationId().equals(medication.getId())).toList(),
                marks.stream().filter(value -> value.getMedicationId().equals(medication.getId())).map(medicationMapper::intake).toList())).toList();
        return new ReportSourceData(owner.getDataRevision(), selection,
                selection.sections().contains(ReportSection.RATINGS)
                        ? ratings.findByUserIdAndLocalDateBetweenOrderByLocalDateAscSlotAsc(owner.getId(), selection.from(), selection.to()) : List.of(),
                notes, selection.sections().contains(ReportSection.TAGS) ? selectedTags : List.of(),
                selection.sections().contains(ReportSection.MEDICATIONS) ? selected : List.of(),
                selection.sections().contains(ReportSection.MEDICATIONS) ? plan : List.of(),
                selection.sections().contains(ReportSection.MEDICATIONS) ? marks : List.of(),
                selection.sections().contains(ReportSection.MEDICATIONS) ? details : List.of());
    }

    private static List<UUID> ids(List<UUID> values) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > 3000 || values.stream().anyMatch(Objects::isNull)
                || values.stream().distinct().count() != values.size()) {
            throw ApiException.invalid("Список источников содержит дубли или слишком много идентификаторов");
        }
        return values.stream().sorted().toList();
    }
}
