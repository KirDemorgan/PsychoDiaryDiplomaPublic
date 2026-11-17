package alex.drankov.diary.medications.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.common.idempotency.service.Idempotency;
import alex.drankov.diary.medications.dto.CreateMedicationRequest;
import alex.drankov.diary.medications.dto.CreateScheduleRequest;
import alex.drankov.diary.medications.dto.ScheduleSlotRequest;
import alex.drankov.diary.medications.dto.UpdateMedicationRequest;
import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeEvent;
import alex.drankov.diary.medications.entity.IntakeKind;
import alex.drankov.diary.medications.entity.Medication;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import static alex.drankov.diary.TestFixtures.CLOCK;
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
class MedicationServiceTest {
    @Mock
    MedicationRepository medications;
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
    private MedicationService service;
    private final JsonMapper json = new JsonMapper();

    @BeforeEach
    void setup() {
        service = new MedicationService(medications, versions, slots, intakes, new MedicationMapper(), access, idempotency, CLOCK);
        lenient().when(access.lock(owner.getId())).thenReturn(owner);
        lenient().when(medications.findByIdAndUserId(medication.getId(), owner.getId())).thenReturn(Optional.of(medication));
        lenient().when(idempotency.execute(any(), anyString(), any(), any(), any(), any()))
                .thenAnswer(call -> ((Supplier<?>) call.getArgument(5)).get());
    }

    @Test
    @DisplayName("Создание, чтение и список возвращают препарат владельца; PATCH очищает форму")
    void lifecycle() {
        when(medications.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        var created = service.create(owner.getId(), UUID.randomUUID(), new CreateMedicationRequest(" Пример ", "таблетки"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().name()).isEqualTo("Пример");
        assertThat(service.get(owner.getId(), medication.getId()).currentSchedule()).isNull();
        when(medications.list(eq(owner.getId()), eq(false), any())).thenReturn(new PageImpl<>(List.of(medication)));
        assertThat(service.list(owner.getId(), false, 0, 20).items()).hasSize(1);
        var request = json.readValue("{\"name\":\"Новое\",\"form\":null}", UpdateMedicationRequest.class);
        assertThat(service.patch(owner.getId(), medication.getId(), "\"v0\"", request).name()).isEqualTo("Новое");
        assertThat(medication.getForm()).isNull();
        assertThat(owner.getDataRevision()).isEqualTo(2);
    }

    @Test
    @DisplayName("Новая схема закрывает предыдущую без переписывания её слотов")
    void versionedSchedule() {
        var previous = persisted(new ScheduleVersion(medication.getId(), NOW.minusSeconds(86400), null, "UTC"));
        when(versions.findByMedicationIdOrderByEffectiveFromAsc(medication.getId())).thenReturn(List.of(previous));
        when(versions.saveAndFlush(any())).thenAnswer(call -> persisted(call.getArgument(0)));
        when(slots.saveAllAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var request = schedule(NOW.plusSeconds(60).atOffset(ZoneOffset.UTC), List.of(slot()));
        var result = service.schedule(owner.getId(), medication.getId(), "\"v0\"", UUID.randomUUID(), request);
        assertThat(previous.getEffectiveUntil()).isEqualTo(request.effectiveFrom().toInstant());
        assertThat(result.body().timeZone()).isEqualTo("UTC");
        verify(versions).flush();
        verify(slots).saveAllAndFlush(any());
        assertThat(service.history(owner.getId(), medication.getId())).hasSize(1);
        assertThat(service.get(owner.getId(), medication.getId()).currentSchedule()).isNotNull();
    }

    @Test
    @DisplayName("История не допускает вставки перед запланированной версией, архивированный препарат не получает схему")
    void scheduleConflicts() {
        var future = persisted(new ScheduleVersion(medication.getId(), NOW.plusSeconds(3600), null, "UTC"));
        when(versions.findByMedicationIdOrderByEffectiveFromAsc(medication.getId())).thenReturn(List.of(future));
        assertThatThrownBy(() -> service.schedule(owner.getId(), medication.getId(), "\"v0\"", UUID.randomUUID(),
                schedule(NOW.atOffset(ZoneOffset.UTC), List.of(slot())))).isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("SCHEDULE_HISTORY_CONFLICT"));
        assertThat(future.getEffectiveUntil()).isNull();
        medication.archive(NOW);
        assertThatThrownBy(() -> service.schedule(owner.getId(), medication.getId(), "\"v0\"", UUID.randomUUID(),
                schedule(NOW.atOffset(ZoneOffset.UTC), List.of(slot())))).isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("MEDICATION_ARCHIVED"));
    }

    @Test
    @DisplayName("Архивирование сохраняет историю; явное удаление очищает ответы повторов препарата и приёмов")
    void archiveAndDelete() {
        assertThat(service.archive(owner.getId(), medication.getId(), "\"v0\"", UUID.randomUUID()).body().archivedAt()).isEqualTo(NOW);
        service.archive(owner.getId(), medication.getId(), "\"v0\"", UUID.randomUUID());
        assertThat(owner.getDataRevision()).isEqualTo(1);
        var intake = persisted(new IntakeEvent(owner.getId(), medication.getId(), null, null, IntakeKind.UNPLANNED));
        when(intakes.findByUserIdAndMedicationId(owner.getId(), medication.getId())).thenReturn(List.of(intake));
        service.delete(owner.getId(), medication.getId(), "\"v0\"");
        verify(idempotency).erase(owner.getId(), medication.getId());
        verify(idempotency).erase(owner.getId(), intake.getId());
        verify(medications).delete(medication);
    }

    @Test
    @DisplayName("Чужой владелец и устаревшая версия не изменяют препарат")
    void ownershipAndVersion() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), medication.getId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.patch(owner.getId(), medication.getId(), "\"v1\"",
                json.readValue("{\"name\":\"Новое\"}", UpdateMedicationRequest.class))).isInstanceOf(ApiException.class);
        assertThat(medication.getName()).isEqualTo("Пример");
        assertThatThrownBy(() -> service.patch(owner.getId(), medication.getId(), "\"v0\"", new UpdateMedicationRequest()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Некорректные названия, дозы, даты и пересекающиеся слоты отклоняются до записи")
    void validation() {
        for (String name : new String[]{null, " ", "а".repeat(201)}) {
            assertThatThrownBy(() -> service.create(owner.getId(), UUID.randomUUID(), new CreateMedicationRequest(name, null)))
                    .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> service.create(owner.getId(), UUID.randomUUID(), new CreateMedicationRequest("Пример", "а".repeat(101))))
                .isInstanceOf(ApiException.class);
        for (BigDecimal dose : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("0.00001"), new BigDecimal("10000000000")}) {
            assertThatThrownBy(() -> MedicationService.validateDose(dose)).isInstanceOf(ApiException.class);
        }
        for (var request : List.of(schedule(NOW.minusSeconds(1).atOffset(ZoneOffset.UTC), List.of(slot())),
                schedule(NOW.atOffset(ZoneOffset.UTC), List.of()), schedule(NOW.atOffset(ZoneOffset.UTC), List.of(slot(), slot())),
                new CreateScheduleRequest(NOW.atOffset(ZoneOffset.UTC), LocalDate.of(2026, 1, 1), "UTC", List.of(slot())),
                schedule(NOW.atOffset(ZoneOffset.UTC), List.of(new ScheduleSlotRequest(null, Set.of(), BigDecimal.ONE, null))))) {
            assertThatThrownBy(() -> service.schedule(owner.getId(), medication.getId(), "\"v0\"", UUID.randomUUID(), request))
                    .isInstanceOf(ApiException.class);
        }
        verify(versions, never()).saveAndFlush(any());
    }

    private ScheduleSlotRequest slot() {
        return new ScheduleSlotRequest(LocalTime.NOON, Set.of(DayOfWeek.TUESDAY), BigDecimal.ONE, DoseUnit.TABLET);
    }

    private CreateScheduleRequest schedule(
            OffsetDateTime from,
            List<ScheduleSlotRequest> slots
    ) {
        return new CreateScheduleRequest(from, null, "UTC", slots);
    }
}
