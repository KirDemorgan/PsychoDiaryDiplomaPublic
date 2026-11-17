package alex.drankov.diary.medications.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.medications.dto.CreateIntakeRequest;
import alex.drankov.diary.medications.dto.UpdateIntakeRequest;
import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeEvent;
import alex.drankov.diary.medications.entity.IntakeKind;
import alex.drankov.diary.medications.entity.IntakeStatus;
import alex.drankov.diary.medications.entity.Medication;
import alex.drankov.diary.medications.entity.ScheduleSlot;
import alex.drankov.diary.medications.entity.ScheduleVersion;
import alex.drankov.diary.medications.mapper.MedicationMapper;
import alex.drankov.diary.medications.repository.IntakeRepository;
import alex.drankov.diary.medications.repository.MedicationRepository;
import alex.drankov.diary.medications.repository.ScheduleSlotRepository;
import alex.drankov.diary.medications.repository.ScheduleVersionRepository;
import alex.drankov.diary.users.entity.User;
import alex.drankov.diary.users.service.UserAccess;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import tools.jackson.databind.json.JsonMapper;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntakeServiceTest {
    @Mock
    MedicationService medications;
    @Mock
    MedicationRepository medicationRepository;
    @Mock
    ScheduleVersionRepository versions;
    @Mock
    ScheduleSlotRepository slots;
    @Mock
    IntakeRepository intakes;
    @Mock
    UserAccess access;
    @Mock
    Idempotency idempotency;
    private final User owner = user();
    private final Medication medication = persisted(new Medication(owner.getId(), "Пример", null));
    private final ScheduleVersion version = persisted(new ScheduleVersion(medication.getId(), NOW.minusSeconds(86400), null, "UTC"));
    private final ScheduleSlot slot = persisted(new ScheduleSlot(medication.getId(), version.getId(), LocalTime.of(10, 0),
            Set.of(DayOfWeek.TUESDAY), BigDecimal.ONE, DoseUnit.TABLET));
    private final JsonMapper json = new JsonMapper();
    private IntakeService service;

    @BeforeEach
    void setup() {
        var mapper = new MedicationMapper();
        service = new IntakeService(medications, medicationRepository, versions, slots, intakes,
                new OccurrenceCalculator(mapper), mapper, access, idempotency);
        lenient().when(access.lock(owner.getId())).thenReturn(owner);
        lenient().when(access.read(owner.getId())).thenReturn(owner);
        lenient().when(medications.find(owner.getId(), medication.getId())).thenReturn(medication);
        lenient().when(slots.findById(slot.getId())).thenReturn(Optional.of(slot));
        lenient().when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        lenient().when(intakes.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        lenient().when(idempotency.execute(any(), anyString(), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(5)).get());
    }

    @Test
    @DisplayName("Плановая отметка связывается с проверенной дозой, повтор под другим ключом не создаёт дубликат")
    void planned() {
        var request = planned(IntakeStatus.TAKEN, NOW, BigDecimal.ONE, DoseUnit.TABLET);
        var response = service.create(owner.getId(), UUID.randomUUID(), request);
        assertThat(response.body().plannedAt()).isEqualTo(NOW);
        assertThat(response.body().scheduleSlotId()).isEqualTo(slot.getId());
        var existing = persisted(new IntakeEvent(owner.getId(), medication.getId(), slot.getId(), NOW, IntakeKind.PLANNED));
        when(intakes.findByScheduleSlotIdAndPlannedAt(slot.getId(), NOW)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create(owner.getId(), UUID.randomUUID(), request)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("INTAKE_ALREADY_EXISTS"));
        assertThat(owner.getDataRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("PATCH переводит приём в явный пропуск и обратно; удаление возвращает дозу к отсутствию данных")
    void changeAndDelete() {
        var intake = persisted(new IntakeEvent(owner.getId(), medication.getId(), slot.getId(), NOW, IntakeKind.PLANNED));
        intake.mark(IntakeStatus.TAKEN, NOW, BigDecimal.ONE, DoseUnit.TABLET);
        when(intakes.findByIdAndUserId(intake.getId(), owner.getId())).thenReturn(Optional.of(intake));
        assertThat(service.get(owner.getId(), intake.getId()).status()).isEqualTo(IntakeStatus.TAKEN);
        assertThat(service.patch(owner.getId(), intake.getId(), "\"v0\"",
                json.readValue("{\"status\":\"SKIPPED\"}", UpdateIntakeRequest.class)).actualAt()).isNull();
        var body = json.readValue("{\"status\":\"TAKEN\",\"actualAt\":\"2026-10-13T10:00:00Z\",\"actualDose\":2,\"unit\":\"TABLET\"}",
                UpdateIntakeRequest.class);
        assertThat(service.patch(owner.getId(), intake.getId(), "\"v0\"", body).actualDose()).isEqualByComparingTo("2");
        var doseOnly = json.readValue("{\"actualDose\":3}", UpdateIntakeRequest.class);
        assertThat(service.patch(owner.getId(), intake.getId(), "\"v0\"", doseOnly).actualDose()).isEqualByComparingTo("3");
        assertThatThrownBy(() -> service.patch(owner.getId(), intake.getId(), "\"v0\"", new UpdateIntakeRequest()))
                .isInstanceOf(ApiException.class);
        service.delete(owner.getId(), intake.getId(), "\"v0\"");
        verify(idempotency).erase(owner.getId(), intake.getId());
        verify(intakes).delete(intake);
    }

    @Test
    @DisplayName("Внеплановый приём имеет фактическую дозу и не содержит ссылки на план")
    void unplanned() {
        var request = new CreateIntakeRequest(IntakeKind.UNPLANNED, null, medication.getId(), IntakeStatus.TAKEN, NOW,
                BigDecimal.ONE, DoseUnit.MG);
        assertThat(service.create(owner.getId(), UUID.randomUUID(), request).body().plannedAt()).isNull();
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("План и журнал учитывают зону пользователя и выбранный препарат без изменения данных")
    void reads() {
        when(medicationRepository.findByUserIdOrderByCreatedAtAscIdAsc(owner.getId())).thenReturn(List.of(medication));
        when(versions.forUser(owner.getId())).thenReturn(List.of(version));
        when(slots.forUser(owner.getId())).thenReturn(List.of(slot));
        var day = LocalDate.of(2026, 10, 13);
        assertThat(service.occurrences(owner.getId(), day, day, null, null).items()).hasSize(1);
        assertThat(service.occurrences(owner.getId(), day, day, "UTC", medication.getId()).items()).hasSize(1);
        when(intakes.list(eq(owner.getId()), any(), any(), eq(medication.getId()), any())).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(owner.getId(), day, day, null, medication.getId(), 0, 20).items()).isEmpty();
        assertThat(owner.getDataRevision()).isZero();
    }

    @Test
    @DisplayName("Неплановое время, архив и чужой препарат не принимаются как плановая доза")
    void invalidOccurrence() {
        assertThatThrownBy(() -> service.create(owner.getId(), UUID.randomUUID(),
                new CreateIntakeRequest(IntakeKind.PLANNED, OccurrenceCalculator.id(slot.getId(), NOW.plusSeconds(1)),
                        null, IntakeStatus.SKIPPED, null, null, null))).isInstanceOf(ApiException.class);
        medication.archive(NOW);
        assertThatThrownBy(() -> service.create(owner.getId(), UUID.randomUUID(), planned(IntakeStatus.SKIPPED, null, null, null)))
                .isInstanceOf(ApiException.class);
        when(medications.find(owner.getId(), medication.getId())).thenThrow(ApiException.notFound());
        assertThatThrownBy(() -> service.create(owner.getId(), UUID.randomUUID(), planned(IntakeStatus.SKIPPED, null, null, null)))
                .isInstanceOf(ApiException.class);
        verify(intakes, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Невалидные сочетания вида, статуса и фактических полей не сохраняются")
    void invalidMarks() {
        var invalid = Arrays.asList(
                new CreateIntakeRequest(null, null, null, null, null, null, null),
                new CreateIntakeRequest(IntakeKind.PLANNED, null, null, IntakeStatus.SKIPPED, null, null, null),
                new CreateIntakeRequest(IntakeKind.PLANNED, "bad", medication.getId(), IntakeStatus.SKIPPED, null, null, null),
                new CreateIntakeRequest(IntakeKind.UNPLANNED, "bad", medication.getId(), IntakeStatus.TAKEN, NOW, BigDecimal.ONE, DoseUnit.MG),
                new CreateIntakeRequest(IntakeKind.UNPLANNED, null, null, IntakeStatus.TAKEN, NOW, BigDecimal.ONE, DoseUnit.MG),
                new CreateIntakeRequest(IntakeKind.UNPLANNED, null, medication.getId(), IntakeStatus.SKIPPED, null, null, null),
                planned(IntakeStatus.NO_DATA, null, null, null), planned(null, null, null, null),
                planned(IntakeStatus.TAKEN, null, BigDecimal.ONE, DoseUnit.MG),
                planned(IntakeStatus.TAKEN, NOW, BigDecimal.ONE, null),
                planned(IntakeStatus.TAKEN, NOW, BigDecimal.ZERO, DoseUnit.MG),
                planned(IntakeStatus.SKIPPED, NOW, null, null), planned(IntakeStatus.SKIPPED, null, BigDecimal.ONE, null),
                planned(IntakeStatus.SKIPPED, null, null, DoseUnit.MG));
        for (var request : invalid) {
            assertThatThrownBy(() -> service.create(owner.getId(), UUID.randomUUID(), request)).isInstanceOf(ApiException.class);
        }
        assertThat(service.create(owner.getId(), UUID.randomUUID(), planned(IntakeStatus.SKIPPED, null, null, null))
                .body().status()).isEqualTo(IntakeStatus.SKIPPED);
    }

    private CreateIntakeRequest planned(
            IntakeStatus status,
            Instant at,
            BigDecimal dose,
            DoseUnit unit
    ) {
        return new CreateIntakeRequest(IntakeKind.PLANNED, OccurrenceCalculator.id(slot.getId(), NOW), null, status, at, dose, unit);
    }
}
