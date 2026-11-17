package alex.drankov.diary.medications.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.api.PageResponse;
import alex.drankov.diary.common.api.TimeRules;
import alex.drankov.diary.common.api.Versions;
import alex.drankov.diary.common.idempotency.dto.MutationResult;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.medications.dto.CreateIntakeRequest;
import alex.drankov.diary.medications.dto.IntakeResponse;
import alex.drankov.diary.medications.dto.OccurrenceListResponse;
import alex.drankov.diary.medications.dto.OccurrenceResponse;
import alex.drankov.diary.medications.dto.UpdateIntakeRequest;
import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeEvent;
import alex.drankov.diary.medications.entity.IntakeKind;
import alex.drankov.diary.medications.entity.IntakeStatus;
import alex.drankov.diary.medications.entity.ScheduleSlot;
import alex.drankov.diary.medications.entity.ScheduleVersion;
import alex.drankov.diary.medications.mapper.MedicationMapper;
import alex.drankov.diary.medications.repository.IntakeRepository;
import alex.drankov.diary.medications.repository.MedicationRepository;
import alex.drankov.diary.medications.repository.ScheduleSlotRepository;
import alex.drankov.diary.medications.repository.ScheduleVersionRepository;
import alex.drankov.diary.users.service.UserAccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntakeService {
    private final MedicationService medications;
    private final MedicationRepository medicationRepository;
    private final ScheduleVersionRepository versions;
    private final ScheduleSlotRepository slots;
    private final IntakeRepository intakes;
    private final OccurrenceCalculator calculator;
    private final MedicationMapper mapper;
    private final UserAccess access;
    private final Idempotency idempotency;

    public OccurrenceListResponse occurrences(
            UUID user,
            LocalDate from,
            LocalDate to,
            String timeZone,
            UUID medicationId
    ) {
        TimeRules.range(from, to, 366);
        var zone = TimeRules.zone(timeZone == null ? access.read(user).getTimeZone() : timeZone);
        var selected = medicationId == null ? medicationRepository.findByUserIdOrderByCreatedAtAscIdAsc(user)
                : List.of(medications.find(user, medicationId));
        var history = versions.forUser(user).stream().collect(Collectors.groupingBy(ScheduleVersion::getMedicationId));
        var scheduleSlots = slots.forUser(user).stream().collect(Collectors.groupingBy(ScheduleSlot::getScheduleVersionId));
        var marks = intakes.findByUserIdOrderByCreatedAtAscIdAsc(user);
        var result = new ArrayList<OccurrenceResponse>();
        var rangeStart = from.atStartOfDay(zone).toInstant();
        var rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        for (var medication : selected) {
            for (var version : history.getOrDefault(medication.getId(), List.of())) {
                result.addAll(calculator.calculate(medication, version,
                        scheduleSlots.getOrDefault(version.getId(), List.of()), marks, rangeStart, rangeEnd));
            }
        }
        result.sort(Comparator.comparing(OccurrenceResponse::plannedAt).thenComparing(OccurrenceResponse::occurrenceId));
        return new OccurrenceListResponse(from, to, zone.getId(), List.copyOf(result));
    }

    public IntakeResponse get(
            UUID user,
            UUID id
    ) {
        return mapper.intake(find(user, id));
    }

    public PageResponse<IntakeResponse> list(UUID user, LocalDate from, LocalDate to, String timeZone,
            UUID medicationId, int page, int size) {
        TimeRules.range(from, to, 366);
        var zone = TimeRules.zone(timeZone == null ? access.read(user).getTimeZone() : timeZone);
        if (medicationId != null) {
            medications.find(user, medicationId);
        }
        return PageResponse.of(intakes.list(user, from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant(), medicationId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))), mapper::intake);
    }

    @Transactional
    public MutationResult<IntakeResponse> create(
            UUID user,
            UUID key,
            CreateIntakeRequest request
    ) {
        var owner = access.lock(user);
        return idempotency.execute(user, "POST /intakes", key, request, IntakeResponse.class, () -> {
            if (request.kind() == null) {
                throw ApiException.invalid("Укажите вид приёма");
            }
            IntakeEvent intake;
            if (request.kind() == IntakeKind.PLANNED) {
                if (request.occurrenceId() == null || request.medicationId() != null) {
                    throw ApiException.invalid("Для планового приёма укажите только occurrenceId");
                }
                var parts = OccurrenceCalculator.decode(request.occurrenceId());
                var slot = slots.findById(UUID.fromString(parts[0])).orElseThrow(ApiException::notFound);
                var medication = medications.find(user, slot.getMedicationId());
                var version = versions.findById(slot.getScheduleVersionId()).orElseThrow(ApiException::notFound);
                Instant at = Instant.parse(parts[1]);
                var zone = TimeRules.zone(version.getTimeZone());
                var day = at.atZone(zone).toLocalDate();
                if (!OccurrenceCalculator.plannedAt(day, slot, zone).equals(at)
                        || !OccurrenceCalculator.valid(medication, version, slot, day, at)) {
                    throw ApiException.notFound();
                }
                if (intakes.findByScheduleSlotIdAndPlannedAt(slot.getId(), at).isPresent()) {
                    throw ApiException.conflict("INTAKE_ALREADY_EXISTS", "Для плановой дозы уже есть отметка");
                }
                intake = new IntakeEvent(user, medication.getId(), slot.getId(), at, request.kind());
            } else {
                if (request.medicationId() == null || request.occurrenceId() != null) {
                    throw ApiException.invalid("Для внепланового приёма укажите только medicationId");
                }
                medications.find(user, request.medicationId());
                intake = new IntakeEvent(user, request.medicationId(), null, null, request.kind());
            }
            validateMark(intake.getKind(), request.status(), request.actualAt(), request.actualDose(), request.unit());
            intake.mark(request.status(), request.actualAt(), request.actualDose(), request.unit());
            intakes.saveAndFlush(intake);
            owner.advanceDataRevision();
            return new MutationResult<>(mapper.intake(intake), intake.getId(), intake.getVersion(), 201);
        });
    }

    @Transactional
    public IntakeResponse patch(
            UUID user,
            UUID id,
            String ifMatch,
            UpdateIntakeRequest request
    ) {
        var owner = access.lock(user);
        var intake = find(user, id);
        Versions.require(ifMatch, intake.getVersion());
        request.validate();
        var status = request.hasStatus() ? request.getStatus() : intake.getStatus();
        boolean clearing = status == IntakeStatus.SKIPPED && intake.getStatus() != status;
        var at = request.hasActualAt() ? request.getActualAt() : clearing ? null : intake.getActualAt();
        var dose = request.hasActualDose() ? request.getActualDose() : clearing ? null : intake.getActualDose();
        var unit = request.hasUnit() ? request.getUnit() : clearing ? null : intake.getUnit();
        validateMark(intake.getKind(), status, at, dose, unit);
        intake.mark(status, at, dose, unit);
        intakes.flush();
        owner.advanceDataRevision();
        return mapper.intake(intake);
    }

    @Transactional
    public void delete(
            UUID user,
            UUID id,
            String ifMatch
    ) {
        var owner = access.lock(user);
        var intake = find(user, id);
        Versions.require(ifMatch, intake.getVersion());
        idempotency.erase(user, id);
        intakes.delete(intake);
        owner.advanceDataRevision();
    }

    private IntakeEvent find(
            UUID user,
            UUID id
    ) {
        return intakes.findByIdAndUserId(id, user).orElseThrow(ApiException::notFound);
    }

    private static void validateMark(
            IntakeKind kind,
            IntakeStatus status,
            Instant at,
            BigDecimal dose,
            DoseUnit unit
    ) {
        if (status == null || status == IntakeStatus.NO_DATA || (kind == IntakeKind.UNPLANNED && status != IntakeStatus.TAKEN)) {
            throw ApiException.invalid("Укажите принято или явно пропущено; внеплановый приём может быть только принят");
        }
        if (status == IntakeStatus.SKIPPED) {
            if (at != null || dose != null || unit != null) {
                throw ApiException.invalid("Пропущенный приём не содержит фактических времени и дозы");
            }
        } else {
            if (at == null || unit == null) {
                throw ApiException.invalid("Для принятой дозы укажите фактические время, дозу и единицу");
            }
            MedicationService.validateDose(dose);
        }
    }
}
