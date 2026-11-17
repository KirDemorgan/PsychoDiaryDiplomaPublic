package alex.drankov.diary.analytics.service;

import alex.drankov.diary.analytics.dto.ComparisonExcludedResponse;
import alex.drankov.diary.analytics.dto.ComparisonGroupResponse;
import alex.drankov.diary.analytics.dto.ComparisonResponse;
import alex.drankov.diary.analytics.dto.ContextAmbiguityResponse;
import alex.drankov.diary.analytics.dto.CoverageResponse;
import alex.drankov.diary.analytics.dto.DailyValueResponse;
import alex.drankov.diary.analytics.dto.HighAnxietyDayResponse;
import alex.drankov.diary.analytics.dto.MedicationAdherenceResponse;
import alex.drankov.diary.analytics.dto.MetricCoverageResponse;
import alex.drankov.diary.analytics.dto.OverviewResponse;
import alex.drankov.diary.analytics.dto.PeriodResponse;
import alex.drankov.diary.analytics.dto.SeriesPointResponse;
import alex.drankov.diary.analytics.dto.TagFrequencyResponse;
import alex.drankov.diary.analytics.entity.Metric;
import alex.drankov.diary.entries.entity.DiaryEntry;
import alex.drankov.diary.medications.dto.OccurrenceResponse;
import alex.drankov.diary.medications.entity.IntakeEvent;
import alex.drankov.diary.medications.entity.IntakeKind;
import alex.drankov.diary.medications.entity.IntakeStatus;
import alex.drankov.diary.medications.entity.Medication;
import alex.drankov.diary.ratings.entity.RatingSlot;
import alex.drankov.diary.ratings.entity.StateRating;
import alex.drankov.diary.tags.entity.EntryTag;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class AnalyticsCalculator {
    public OverviewResponse overview(
            PeriodResponse period,
            long revision,
            List<StateRating> ratings,
            List<EntryTag> tags,
            List<Medication> medications,
            List<OccurrenceResponse> occurrences,
            List<IntakeEvent> intakes,
            int threshold
    ) {
        var days = period.from().datesUntil(period.to().plusDays(1)).toList();
        var daily = daily(days, ratings);
        var ratingsByDay = ratings.stream().collect(Collectors.groupingBy(StateRating::getLocalDate));
        var series = new EnumMap<Metric, List<SeriesPointResponse>>(Metric.class);
        var coverage = new EnumMap<Metric, MetricCoverageResponse>(Metric.class);
        for (var metric : Metric.values()) {
            var points = new ArrayList<SeriesPointResponse>();
            for (var day : days) {
                for (var slot : RatingSlot.values()) {
                    Integer value = ratingsByDay.getOrDefault(day, List.of()).stream()
                            .filter(r -> r.getSlot() == slot).findFirst().map(metric::value).orElse(null);
                    points.add(new SeriesPointResponse(day, slot, value));
                }
            }
            long filled = points.stream().filter(point -> point.value() != null).count();
            long filledDays = points.stream().filter(point -> point.value() != null).map(SeriesPointResponse::date).distinct().count();
            series.put(metric, List.copyOf(points));
            coverage.put(metric, new MetricCoverageResponse(filled, days.size() * 3L, filledDays, days.size()));
        }
        var observed = tags.stream().filter(EntryTag::observed).filter(tag -> inPeriod(period, tag.getAppliesToLocalDate())).toList();
        var observedByCode = observed.stream().collect(Collectors.groupingBy(EntryTag::getLabelCode));
        var frequencies = Arrays.stream(TagCode.values()).map(code -> {
            var values = observedByCode.getOrDefault(code, List.of());
            return new TagFrequencyResponse(code, values.size(), values.stream().map(EntryTag::getAppliesToLocalDate).distinct().count());
        }).toList();
        var occurrencesByMedication = occurrences.stream().collect(Collectors.groupingBy(OccurrenceResponse::medicationId));
        var zone = ZoneId.of(period.timeZone());
        var adherence = medications.stream().map(medication -> {
            var planned = occurrencesByMedication.getOrDefault(medication.getId(), List.of());
            long taken = planned.stream().filter(o -> o.status() == IntakeStatus.TAKEN).count();
            long skipped = planned.stream().filter(o -> o.status() == IntakeStatus.SKIPPED).count();
            long unplanned = intakes.stream().filter(i -> i.getKind() == IntakeKind.UNPLANNED
                    && i.getMedicationId().equals(medication.getId()) && inPeriod(period, i.getActualAt().atZone(zone).toLocalDate())).count();
            return new MedicationAdherenceResponse(medication.getId(), medication.getName(), planned.size(), taken, skipped,
                    planned.size() - taken - skipped, ratio(taken, planned.size()), ratio(taken + skipped, planned.size()), unplanned);
        }).toList();
        var high = daily.stream().filter(day -> day.anxietyMean() != null
                && day.anxietyMean().compareTo(BigDecimal.valueOf(threshold)) >= 0)
                .map(day -> new HighAnxietyDayResponse(day.date(), day.anxietyMean(), day.anxietySlotCount(), threshold,
                        "DAILY_MEAN_AT_LEAST_THRESHOLD")).toList();
        return new OverviewResponse(period, revision, new CoverageResponse(Collections.unmodifiableMap(coverage),
                ratings.size(), days.size() * 3L, ratings.stream().map(StateRating::getLocalDate).distinct().count(), days.size()),
                Collections.unmodifiableMap(series), daily, frequencies, adherence, high, ambiguities(period, tags),
                List.of("Пропуски не интерполируются", "Порог тревоги — правило интерфейса, не клинический критерий",
                        "Дни имеют одинаковый вес; дополнительные записи его не увеличивают"));
    }

    public ComparisonResponse comparison(
            PeriodResponse period,
            long revision,
            List<StateRating> ratings,
            List<DiaryEntry> entries,
            List<EntryTag> tags,
            TagCode code,
            Metric metric
    ) {
        var zone = ZoneId.of(period.timeZone());
        var with = new ArrayList<LocalDate>();
        var without = new ArrayList<LocalDate>();
        var means = new HashMap<LocalDate, BigDecimal>();
        int noRating = 0;
        int noEntry = 0;
        int incomplete = 0;
        int ambiguous = 0;
        var ratingsByDay = ratings.stream().collect(Collectors.groupingBy(StateRating::getLocalDate));
        var entriesByDay = entries.stream().collect(Collectors.groupingBy(e -> e.getOccurredAt().atZone(zone).toLocalDate()));
        var ambiguousDays = ambiguities(period, tags).stream().filter(value -> value.codes().contains(code))
                .map(ContextAmbiguityResponse::date).collect(Collectors.toSet());
        var mentionedDays = tags.stream().filter(tag -> tag.observed() && tag.getLabelCode() == code)
                .map(EntryTag::getAppliesToLocalDate).filter(Objects::nonNull).collect(Collectors.toSet());
        for (var day : period.from().datesUntil(period.to().plusDays(1)).toList()) {
            var values = ratingsByDay.getOrDefault(day, List.of()).stream().map(metric::value).filter(Objects::nonNull).toList();
            if (values.isEmpty()) {
                noRating++;
                continue;
            }
            var context = entriesByDay.getOrDefault(day, List.of());
            if (context.isEmpty()) {
                noEntry++;
                continue;
            }
            if (context.stream().anyMatch(e -> !e.isReviewComplete())) {
                incomplete++;
                continue;
            }
            if (ambiguousDays.contains(day)) {
                ambiguous++;
                continue;
            }
            means.put(day, mean(values));
            (mentionedDays.contains(day) ? with : without).add(day);
        }
        var a = group(with, means);
        var b = group(without, means);
        boolean sufficient = with.size() >= 5 && without.size() >= 5;
        return new ComparisonResponse(code, metric, period, sufficient ? "SUFFICIENT" : "INSUFFICIENT_DATA", a, b,
                new ComparisonExcludedResponse(noRating, noEntry, incomplete, ambiguous),
                sufficient ? a.mean().subtract(b.mean()) : null,
                List.of("Без упоминания не означает доказанное отсутствие события", "Сравнение описывает связь, а не причину",
                        "Пять дней — правило достаточности интерфейса, не проверка статистической значимости"), revision);
    }

    public List<DailyValueResponse> daily(
            List<LocalDate> days,
            List<StateRating> ratings
    ) {
        var ratingsByDay = ratings.stream().collect(Collectors.groupingBy(StateRating::getLocalDate));
        return days.stream().map(day -> {
            var selected = ratingsByDay.getOrDefault(day, List.of());
            var anxiety = selected.stream().map(StateRating::getAnxiety).filter(Objects::nonNull).toList();
            var mood = selected.stream().map(StateRating::getMood).filter(Objects::nonNull).toList();
            var energy = selected.stream().map(StateRating::getEnergy).filter(Objects::nonNull).toList();
            return new DailyValueResponse(day, mean(anxiety), anxiety.size(), mean(mood), mood.size(), mean(energy), energy.size());
        }).toList();
    }

    public List<ContextAmbiguityResponse> ambiguities(
            PeriodResponse period,
            List<EntryTag> tags
    ) {
        var result = new ArrayList<ContextAmbiguityResponse>();
        var evidenceByDay = tags.stream()
                .filter(tag -> tag.getAppliesToLocalDate() != null
                        && tag.getSubject() == TagSubject.SELF
                        && tag.getTemporal() != TagTemporal.FUTURE)
                .collect(Collectors.groupingBy(EntryTag::getAppliesToLocalDate,
                        Collectors.groupingBy(EntryTag::getLabelCode)));
        for (var day : period.from().datesUntil(period.to().plusDays(1)).toList()) {
            var evidenceByCode = evidenceByDay.getOrDefault(day, Map.of());
            var codes = new ArrayList<TagCode>();
            for (var code : TagCode.values()) {
                var presences = evidenceByCode.getOrDefault(code, List.of()).stream()
                        .map(EntryTag::getPresence).collect(Collectors.toSet());
                boolean uncertain = presences.contains(TagPresence.UNCERTAIN);
                boolean contradictory = presences.contains(TagPresence.PRESENT) && presences.contains(TagPresence.NEGATED);
                if (uncertain || contradictory) {
                    codes.add(code);
                }
            }
            if (!codes.isEmpty()) {
                result.add(new ContextAmbiguityResponse(day, List.copyOf(codes)));
            }
        }
        return List.copyOf(result);
    }

    private ComparisonGroupResponse group(
            List<LocalDate> dates,
            Map<LocalDate, BigDecimal> means
    ) {
        var total = dates.stream().map(means::get).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ComparisonGroupResponse(dates.size(), dates.isEmpty() ? null
                : total.divide(BigDecimal.valueOf(dates.size()), 4, RoundingMode.HALF_UP), List.copyOf(dates));
    }

    private static BigDecimal mean(List<Integer> values) {
        return values.isEmpty() ? null : BigDecimal.valueOf(values.stream().mapToLong(Integer::longValue).sum())
                .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(
            long numerator,
            long denominator
    ) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static boolean inPeriod(
            PeriodResponse period,
            LocalDate day
    ) {
        return !day.isBefore(period.from()) && !day.isAfter(period.to());
    }
}
