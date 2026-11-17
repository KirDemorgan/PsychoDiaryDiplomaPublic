package alex.drankov.diary.medications.service;

import alex.drankov.diary.common.api.ApiException;
import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeEvent;
import alex.drankov.diary.medications.entity.IntakeKind;
import alex.drankov.diary.medications.entity.IntakeStatus;
import alex.drankov.diary.medications.entity.Medication;
import alex.drankov.diary.medications.entity.ScheduleSlot;
import alex.drankov.diary.medications.entity.ScheduleVersion;
import alex.drankov.diary.medications.mapper.MedicationMapper;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static alex.drankov.diary.TestFixtures.NOW;
import static alex.drankov.diary.TestFixtures.persisted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OccurrenceCalculatorTest {
    private final OccurrenceCalculator calculator = new OccurrenceCalculator(new MedicationMapper());

    @Test
    @DisplayName("Неотмеченная плановая доза остаётся неизвестной, внеплановый приём её не закрывает")
    void unknownAndUnplanned() {
        var medication = persisted(new Medication(UUID.randomUUID(), "Пример", null));
        var version = persisted(new ScheduleVersion(medication.getId(), NOW.minusSeconds(86400), null, "UTC"));
        var slot = persisted(new ScheduleSlot(medication.getId(), version.getId(), LocalTime.NOON,
                Set.of(DayOfWeek.TUESDAY), BigDecimal.ONE, DoseUnit.TABLET));
        var unplanned = persisted(new IntakeEvent(medication.getUserId(), medication.getId(), null, null, IntakeKind.UNPLANNED));
        unplanned.mark(IntakeStatus.TAKEN, NOW, BigDecimal.ONE, DoseUnit.TABLET);
        var result = calculator.calculate(medication, version, List.of(slot), List.of(unplanned),
                Instant.parse("2026-10-13T00:00:00Z"), Instant.parse("2026-10-14T00:00:00Z"));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().status()).isEqualTo(IntakeStatus.NO_DATA);
        var mark = persisted(new IntakeEvent(medication.getUserId(), medication.getId(), slot.getId(),
                result.getFirst().plannedAt(), IntakeKind.PLANNED));
        mark.mark(IntakeStatus.SKIPPED, null, null, null);
        assertThat(calculator.calculate(medication, version, List.of(slot), List.of(mark),
                Instant.parse("2026-10-13T00:00:00Z"), Instant.parse("2026-10-14T00:00:00Z"))
                .getFirst().status()).isEqualTo(IntakeStatus.SKIPPED);
        assertThat(OccurrenceCalculator.decode(result.getFirst().occurrenceId())[0]).isEqualTo(slot.getId().toString());
        assertThatThrownBy(() -> OccurrenceCalculator.decode("invalid")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> OccurrenceCalculator.decode(Base64.getUrlEncoder().encodeToString("bad@date".getBytes())))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Разрыв DST переносит дозу на следующий существующий момент, повтор времени даёт одну дозу")
    void daylightSaving() {
        var medication = new Medication(UUID.randomUUID(), "Пример", null);
        var version = new ScheduleVersion(medication.getId(), Instant.parse("2026-01-01T00:00:00Z"), null, "America/New_York");
        var slot = new ScheduleSlot(medication.getId(), version.getId(), LocalTime.of(2, 30), Set.of(DayOfWeek.SUNDAY),
                BigDecimal.ONE, DoseUnit.MG);
        assertThat(OccurrenceCalculator.plannedAt(LocalDate.of(2026, 3, 8), slot, ZoneId.of("America/New_York")))
                .isEqualTo(Instant.parse("2026-03-08T07:00:00Z"));
        var overlap = new ScheduleSlot(medication.getId(), version.getId(), LocalTime.of(1, 30), Set.of(DayOfWeek.SUNDAY),
                BigDecimal.ONE, DoseUnit.MG);
        var occurrences = calculator.calculate(medication, version, List.of(overlap), List.of(),
                Instant.parse("2026-11-01T04:00:00Z"), Instant.parse("2026-11-02T05:00:00Z"));
        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.getFirst().plannedAt()).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    @DisplayName("Начало и конец версии, дни недели, окончание лечения и архивирование ограничивают план")
    void boundaries() {
        var medication = new Medication(UUID.randomUUID(), "Пример", null);
        var day = LocalDate.of(2026, 10, 13);
        var at = day.atTime(12, 0).toInstant(ZoneOffset.UTC);
        var version = new ScheduleVersion(medication.getId(), at, day, "UTC");
        var slot = new ScheduleSlot(medication.getId(), version.getId(), LocalTime.NOON, Set.of(DayOfWeek.TUESDAY),
                BigDecimal.ONE, DoseUnit.TABLET);
        assertThat(OccurrenceCalculator.valid(medication, version, slot, day, at)).isTrue();
        assertThat(OccurrenceCalculator.valid(medication, version, slot, day, at.minusSeconds(1))).isFalse();
        assertThat(OccurrenceCalculator.valid(medication, version, slot, day.plusDays(7), at.plusSeconds(604800))).isFalse();
        assertThat(OccurrenceCalculator.valid(medication, version, slot, day.plusDays(1), at.plusSeconds(86400))).isFalse();
        version.close(at.plusSeconds(1));
        assertThat(OccurrenceCalculator.valid(medication, version, slot, day, at.plusSeconds(1))).isFalse();
        medication.archive(at);
        assertThat(OccurrenceCalculator.valid(medication, version, slot, day, at)).isFalse();
    }
}
