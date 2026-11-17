package alex.drankov.diary.analytics.service;

import alex.drankov.diary.analytics.dto.PeriodResponse;
import alex.drankov.diary.analytics.entity.Metric;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.medications.dto.OccurrenceResponse;
import alex.drankov.diary.medications.entity.DoseUnit;
import alex.drankov.diary.medications.entity.IntakeEvent;
import alex.drankov.diary.medications.entity.IntakeKind;
import alex.drankov.diary.medications.entity.IntakeStatus;
import alex.drankov.diary.medications.entity.Medication;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.entity.StateRating;
import alex.drankov.diary.tags.entity.EntryTag;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagOrigin;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static alex.drankov.diary.TestFixtures.persisted;
import static alex.drankov.diary.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsCalculatorTest {
    private final AnalyticsCalculator calculator = new AnalyticsCalculator();
    private final UUID user = UUID.randomUUID();
    private final LocalDate first = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("Ноль остаётся наблюдением, NULL — пропуском; средние и покрытие независимы для каждой метрики")
    void independentMetricsAndGaps() {
        var morning = rating(first, RatingSlot.MORNING, 0, null, 10);
        var evening = rating(first, RatingSlot.EVENING, 10, 6, null);
        var result = calculator.overview(period(2), 3, List.of(morning, evening), List.of(), List.of(), List.of(), List.of(), 7);
        assertThat(result.dailyValues().getFirst().anxietyMean()).isEqualByComparingTo("5");
        assertThat(result.dailyValues().getFirst().anxietySlotCount()).isEqualTo(2);
        assertThat(result.dailyValues().getFirst().moodMean()).isEqualByComparingTo("6");
        assertThat(result.dailyValues().get(1).anxietyMean()).isNull();
        assertThat(result.series().get(Metric.ANXIETY)).hasSize(6);
        assertThat(result.series().get(Metric.ANXIETY).getFirst().value()).isZero();
        assertThat(result.series().get(Metric.ANXIETY).get(1).value()).isNull();
        assertThat(result.coverage().metrics().get(Metric.ANXIETY).filled()).isEqualTo(2);
        assertThat(result.coverage().metrics().get(Metric.MOOD).filled()).isEqualTo(1);
        assertThat(result.coverage().metrics().get(Metric.ENERGY).filled()).isEqualTo(1);
        assertThat(result.coverage().metrics().get(Metric.ANXIETY).expected()).isEqualTo(6);
        assertThat(result.coverage().filledDays()).isEqualTo(1);
        assertThat(result.highAnxietyDays()).isEmpty();
        var high = calculator.overview(period(1), 0, List.of(evening), List.of(), List.of(), List.of(), List.of(), 7);
        assertThat(high.highAnxietyDays().getFirst().mean()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Выполнение схемы учитывает неизвестные дозы, внеплановые события показываются отдельно")
    void adherenceAndTagDays() {
        var medication = persisted(new Medication(user, "Пример", null));
        var empty = persisted(new Medication(user, "Без плана", null));
        var slots = List.of(occurrence(medication, IntakeStatus.TAKEN), occurrence(medication, IntakeStatus.SKIPPED),
                occurrence(medication, IntakeStatus.NO_DATA), occurrence(medication, IntakeStatus.NO_DATA));
        var unplanned = new IntakeEvent(user, medication.getId(), null, null, IntakeKind.UNPLANNED);
        unplanned.mark(IntakeStatus.TAKEN, first.atStartOfDay().toInstant(ZoneOffset.UTC), BigDecimal.ONE, DoseUnit.MG);
        var result = calculator.overview(period(1), 0, List.of(),
                List.of(tag(first, TagPresence.PRESENT), tag(first, TagPresence.PRESENT), tag(first, TagPresence.NEGATED)),
                List.of(medication, empty), slots, List.of(unplanned), 7);
        var adherence = result.medicationAdherence().getFirst();
        assertThat(adherence.planned()).isEqualTo(4);
        assertThat(adherence.noData()).isEqualTo(2);
        assertThat(adherence.takenRate()).isEqualByComparingTo("0.25");
        assertThat(adherence.markedCoverage()).isEqualByComparingTo("0.5");
        assertThat(adherence.unplanned()).isEqualTo(1);
        assertThat(result.medicationAdherence().get(1).takenRate()).isNull();
        var frequency = result.tagFrequencies().stream().filter(v -> v.code() == TagCode.SLEEP_DEFICIT).findFirst().orElseThrow();
        assertThat(frequency.occurrences()).isEqualTo(2);
        assertThat(frequency.days()).isEqualTo(1);
        assertThat(result.ambiguousContext().getFirst().codes()).containsExactly(TagCode.SLEEP_DEFICIT);
    }

    @Test
    @DisplayName("Сравнение даёт каждому проверенному дню один вес; пять дней нужны в обеих группах")
    void dayWeightedComparison() {
        var ratings = new ArrayList<StateRating>();
        var entries = new ArrayList<DiaryEntry>();
        var tags = new ArrayList<EntryTag>();
        for (int i = 0; i < 10; i++) {
            var day = first.plusDays(i);
            ratings.add(rating(day, RatingSlot.MORNING, i < 5 ? 8 : 2, null, null));
            entries.add(entry(day, true));
            if (i < 5) {
                tags.add(tag(day, TagPresence.PRESENT));
                entries.add(entry(day, true));
            }
        }
        var result = calculator.comparison(period(10), 7, ratings, entries, tags, TagCode.SLEEP_DEFICIT, Metric.ANXIETY);
        assertThat(result.status()).isEqualTo("SUFFICIENT");
        assertThat(result.withMention().nDays()).isEqualTo(5);
        assertThat(result.withoutMention().nDays()).isEqualTo(5);
        assertThat(result.delta()).isEqualByComparingTo("6");
        assertThat(result.dataRevision()).isEqualTo(7);
        var insufficient = calculator.comparison(period(9), 7, ratings, entries, tags, TagCode.SLEEP_DEFICIT, Metric.ANXIETY);
        assertThat(insufficient.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(insufficient.delta()).isNull();
    }

    @Test
    @DisplayName("Нет записи, незавершённое ревью, конфликт цитат и отсутствие метрики исключаются явно")
    void unknownAndContradictoryContext() {
        var ratings = List.of(rating(first, RatingSlot.MORNING, 1, null, null),
                rating(first.plusDays(1), RatingSlot.MORNING, 2, null, null),
                rating(first.plusDays(2), RatingSlot.MORNING, 3, null, null));
        var entries = List.of(entry(first.plusDays(1), false), entry(first.plusDays(2), true));
        var tags = List.of(tag(first.plusDays(2), TagPresence.PRESENT), tag(first.plusDays(2), TagPresence.NEGATED));
        var result = calculator.comparison(period(4), 0, ratings, entries, tags, TagCode.SLEEP_DEFICIT, Metric.ANXIETY);
        assertThat(result.excluded().noRating()).isEqualTo(1);
        assertThat(result.excluded().noEntry()).isEqualTo(1);
        assertThat(result.excluded().incompleteContext()).isEqualTo(1);
        assertThat(result.excluded().ambiguousContext()).isEqualTo(1);
        assertThat(result.withMention().mean()).isNull();
        assertThat(result.withoutMention().mean()).isNull();
        assertThat(calculator.ambiguities(period(1), List.of(tag(first, TagPresence.UNCERTAIN)))).hasSize(1);
    }

    private PeriodResponse period(int days) {
        return new PeriodResponse(first, first.plusDays(days - 1), "UTC");
    }

    private StateRating rating(
            LocalDate day,
            RatingSlot slot,
            Integer anxiety,
            Integer mood,
            Integer energy
    ) {
        var value = new StateRating(user, day, slot);
        value.replace(anxiety, mood, energy, "UTC");
        return value;
    }

    private DiaryEntry entry(
            LocalDate day,
            boolean complete
    ) {
        var value = new DiaryEntry(user, "Синтетическая запись", day.atTime(12, 0).toInstant(ZoneOffset.UTC), "UTC", true);
        value.review(UUID.randomUUID(), complete);
        return value;
    }

    private EntryTag tag(
            LocalDate day,
            TagPresence presence
    ) {
        return new EntryTag(UUID.randomUUID(), UUID.randomUUID(), 1, TagCode.SLEEP_DEFICIT, "сон", 0, 3,
                presence, TagTemporal.CURRENT, TagSubject.SELF, day, TagOrigin.MANUAL);
    }

    private OccurrenceResponse occurrence(
            Medication medication,
            IntakeStatus status
    ) {
        return new OccurrenceResponse(UUID.randomUUID().toString(), medication.getId(), medication.getName(), UUID.randomUUID(),
                UUID.randomUUID(), first.atTime(12, 0).toInstant(ZoneOffset.UTC), BigDecimal.ONE, DoseUnit.MG, "UTC", status, null);
    }
}
