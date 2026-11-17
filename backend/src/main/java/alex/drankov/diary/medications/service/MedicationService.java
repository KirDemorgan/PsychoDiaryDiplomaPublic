package alex.drankov.diary.medications.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.Texts;
import alex.drankov.diary.common.api.TimeRules;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.medications.dto.CreateMedicationRequest;
import alex.drankov.diary.medications.dto.CreateScheduleRequest;
import alex.drankov.diary.medications.dto.MedicationResponse;
import alex.drankov.diary.medications.dto.ScheduleSlotRequest;
import alex.drankov.diary.medications.dto.ScheduleVersionResponse;
import alex.drankov.diary.medications.dto.UpdateMedicationRequest;
import alex.drankov.diary.medications.entity.Medication;
import alex.drankov.diary.medications.entity.ScheduleSlot;
import alex.drankov.diary.medications.entity.ScheduleVersion;
import alex.drankov.diary.medications.mapper.MedicationMapper;
import alex.drankov.diary.medications.repository.IntakeRepository;
import alex.drankov.diary.medications.repository.MedicationRepository;
import alex.drankov.diary.medications.repository.ScheduleSlotRepository;
import alex.drankov.diary.medications.repository.ScheduleVersionRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {
    private final MedicationRepository medications;
    private final ScheduleVersionRepository versions;
    private final ScheduleSlotRepository slots;
    private final IntakeRepository intakes;
    private final MedicationMapper mapper;
    private final UserAccess access;
    private final Idempotency idempotency;
    private final Clock clock;

    public Medication find(
            UUID user,
            UUID id
    ) {
        return medications.findByIdAndUserId(id, user).orElseThrow(ApiException::notFound);
    }

    public MedicationResponse get(
            UUID user,
            UUID id
    ) {
        return response(find(user, id));
    }

    public PageResponse<MedicationResponse> list(
            UUID user,
            boolean archived,
            int page,
            int size
    ) {
        return PageResponse.of(medications.list(user, archived,
                PageRequest.of(page, size, Sort.by("createdAt", "id"))), this::response);
    }

    @Transactional
    public MutationResult<MedicationResponse> create(
            UUID user,
            UUID key,
            CreateMedicationRequest request
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "POST /medications", key, request, MedicationResponse.class, () -> {
            validateName(request.name(), request.form());
            var medication = medications.saveAndFlush(new Medication(user, request.name().strip(), request.form()));
            owner.advanceDataRevision();
            return new MutationResult<>(response(medication), medication.getId(), medication.getVersion(), 201);
        });
    }

    @Transactional
    public MedicationResponse patch(
            UUID user,
            UUID id,
            String ifMatch,
            UpdateMedicationRequest request
    ) {
        var owner = access.lock(user);
        var medication = find(user, id);
        Versions.require(ifMatch, medication.getVersion());
        request.validate();
        String name = request.hasName() ? request.getName() : medication.getName();
        String form = request.hasForm() ? request.getForm() : medication.getForm();
        validateName(name, form);
        medication.rename(name.strip(), form);
        medications.flush();
        owner.advanceDataRevision();
        return response(medication);
    }

    @Transactional
    public MutationResult<ScheduleVersionResponse> schedule(
            UUID user, UUID id, String ifMatch, UUID key, CreateScheduleRequest request
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "POST /medications/" + id + "/schedule-versions", key, request,
                ScheduleVersionResponse.class, () -> {
                    var medication = find(user, id);
                    Versions.require(ifMatch, medication.getVersion());
                    if (medication.getArchivedAt() != null) {
                        throw ApiException.conflict("MEDICATION_ARCHIVED", "Препарат архивирован");
                    }
                    validateSchedule(request);
                    var history = versions.findByMedicationIdOrderByEffectiveFromAsc(id);
                    Instant from = request.effectiveFrom().toInstant();
                    if (!history.isEmpty()) {
                        var previous = history.getLast();
                        if (!from.isAfter(previous.getEffectiveFrom())) {
                            throw ApiException.conflict("SCHEDULE_HISTORY_CONFLICT", "Новая схема должна следовать за последней версией");
                        }
                        previous.close(from);
                        versions.flush();
                    }
                    var version = versions.saveAndFlush(new ScheduleVersion(id, from,
                            request.treatmentEndsOn(), request.timeZone()));
                    slots.saveAllAndFlush(request.slots().stream().map(slot -> new ScheduleSlot(id, version.getId(),
                            slot.localTime(), slot.daysOfWeek(), slot.dose(), slot.unit())).toList());
                    medication.touch();
                    medications.flush();
                    owner.advanceDataRevision();
                    return new MutationResult<>(scheduleResponse(version, medication.getVersion()),
                            id, medication.getVersion(), 201);
                });
    }

    public List<ScheduleVersionResponse> history(
            UUID user,
            UUID id
    ) {
        var medication = find(user, id);
        return versions.findByMedicationIdOrderByEffectiveFromAsc(id).stream()
                .map(version -> scheduleResponse(version, medication.getVersion())).toList();
    }

    @Transactional
    public MutationResult<MedicationResponse> archive(
            UUID user,
            UUID id,
            String ifMatch,
            UUID key
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "POST /medications/" + id + "/archive", key, id,
                MedicationResponse.class, () -> {
                    var medication = find(user, id);
                    Versions.require(ifMatch, medication.getVersion());
                    if (medication.getArchivedAt() == null) {
                        medication.archive(clock.instant());
                        medications.flush();
                        owner.advanceDataRevision();
                    }
                    return new MutationResult<>(response(medication), id, medication.getVersion(), 200);
                });
    }

    @Transactional
    public void delete(
            UUID user,
            UUID id,
            String ifMatch
    ) {
        var owner = access.lock(user);
        var medication = find(user, id);
        Versions.require(ifMatch, medication.getVersion());
        intakes.findByUserIdAndMedicationId(user, id)
                .forEach(intake -> idempotency.erase(user, intake.getId()));
        idempotency.erase(user, id);
        medications.delete(medication);
        owner.advanceDataRevision();
    }

    public MedicationResponse response(Medication medication) {
        var current = versions.findByMedicationIdOrderByEffectiveFromAsc(medication.getId()).stream()
                .filter(version -> !version.getEffectiveFrom().isAfter(clock.instant())
                        && (version.getEffectiveUntil() == null || clock.instant().isBefore(version.getEffectiveUntil())))
                .findFirst().map(version -> scheduleResponse(version, medication.getVersion())).orElse(null);
        return new MedicationResponse(medication.getId(), medication.getName(), medication.getForm(),
                medication.getArchivedAt(), medication.getCreatedAt(), medication.getUpdatedAt(),
                medication.getVersion(), current);
    }

    private ScheduleVersionResponse scheduleResponse(
            ScheduleVersion version,
            long medicationVersion
    ) {
        return new ScheduleVersionResponse(version.getId(), version.getMedicationId(), version.getEffectiveFrom(),
                version.getEffectiveUntil(), version.getTreatmentEndsOn(), version.getTimeZone(),
                slots.findByScheduleVersionIdOrderByLocalTimeAscIdAsc(version.getId()).stream().map(mapper::slot).toList(),
                version.getCreatedAt(), medicationVersion);
    }

    private void validateSchedule(CreateScheduleRequest request) {
        TimeRules.occurrence(request.effectiveFrom(), request.timeZone());
        if (request.effectiveFrom().toInstant().isBefore(clock.instant())) {
            throw ApiException.invalid("Нельзя изменять расписание задним числом");
        }
        if (request.treatmentEndsOn() != null && request.treatmentEndsOn().isBefore(request.effectiveFrom().toLocalDate())) {
            throw ApiException.invalid("Дата окончания предшествует началу схемы");
        }
        if (request.slots() == null || request.slots().isEmpty() || request.slots().size() > 24) {
            throw ApiException.invalid("Укажите от 1 до 24 слотов расписания");
        }
        var occupied = new HashSet<String>();
        for (var slot : request.slots()) {
            validateSlot(slot);
            for (var day : slot.daysOfWeek()) {
                if (!occupied.add(day + ":" + slot.localTime())) {
                    throw ApiException.invalid("Слоты одного дня и времени не должны повторяться");
                }
            }
        }
    }

    private static void validateSlot(ScheduleSlotRequest slot) {
        if (slot == null || slot.localTime() == null || slot.daysOfWeek() == null || slot.unit() == null) {
            throw invalidSlot();
        }
        boolean wholeMinute = slot.localTime().getSecond() == 0 && slot.localTime().getNano() == 0;
        boolean validDays = !slot.daysOfWeek().isEmpty() && slot.daysOfWeek().stream().noneMatch(Objects::isNull);
        if (!wholeMinute || !validDays) {
            throw invalidSlot();
        }
        validateDose(slot.dose());
    }

    private static ApiException invalidSlot() {
        return ApiException.invalid("Укажите время с точностью до минуты, дни недели и единицу дозы");
    }

    static void validateDose(BigDecimal dose) {
        if (dose == null || dose.signum() <= 0 || dose.stripTrailingZeros().scale() > 4
                || dose.compareTo(new BigDecimal("10000000000")) >= 0) {
            throw ApiException.invalid("Доза должна быть положительной, до 10 целых и 4 дробных цифр");
        }
    }

    private static void validateName(
            String name,
            String form
    ) {
        if (name == null || name.isBlank() || Texts.exceeds(name, 200)
                || (form != null && Texts.exceeds(form, 100))) {
            throw ApiException.invalid("Укажите название до 200 символов и форму до 100 символов");
        }
    }
}
