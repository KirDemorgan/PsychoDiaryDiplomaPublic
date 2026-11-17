package alex.drankov.diary.reports.service;

import alex.drankov.diary.analytics.dto.ComparisonResponse;
import alex.drankov.diary.analytics.dto.OverviewResponse;
import alex.drankov.diary.analytics.entity.Metric;
import alex.drankov.diary.common.crypto.Hashing;
import alex.drankov.diary.reports.dto.FactResponse;
import alex.drankov.diary.reports.dto.FactValueResponse;
import alex.drankov.diary.reports.dto.SourceRefResponse;
import alex.drankov.diary.reports.entity.FactType;
import alex.drankov.diary.reports.entity.ReportSection;
import alex.drankov.diary.reports.service.model.ReportSourceData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ReportFactBuilder {
    private final JsonMapper json;

    public List<FactResponse> build(
            ReportSourceData data,
            OverviewResponse statistics,
            List<ComparisonResponse> comparisons
    ) {
        var result = new ArrayList<FactResponse>();
        var sections = data.selection().sections();
        if (sections.contains(ReportSection.RATINGS)) {
            for (var metric : Metric.values()) {
                var refs = data.ratings().stream().filter(r -> metric.value(r) != null)
                        .map(r -> new SourceRefResponse("RATING", r.getId(), r.getVersion(), null, r.getLocalDate())).toList();
                var coverage = statistics.coverage().metrics().get(metric);
                result.add(fact(FactType.COVERAGE, new FactValueResponse(metric.name(), null, null,
                        coverage.filled(), coverage.expected(), null, null), refs));
                var values = statistics.dailyValues().stream().map(day -> switch (metric) {
                    case ANXIETY -> day.anxietyMean();
                    case MOOD -> day.moodMean();
                    case ENERGY -> day.energyMean();
                }).filter(Objects::nonNull).toList();
                BigDecimal mean = values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
                result.add(fact(FactType.METRIC_MEAN, new FactValueResponse(metric.name(), mean, null,
                        (long) values.size(), null, null, null), refs));
            }
            for (var day : statistics.highAnxietyDays()) {
                var refs = data.ratings().stream().filter(r -> r.getLocalDate().equals(day.date()) && r.getAnxiety() != null)
                        .map(r -> new SourceRefResponse("RATING", r.getId(), r.getVersion(), null, day.date())).toList();
                result.add(fact(FactType.HIGH_ANXIETY, new FactValueResponse("ANXIETY", day.mean(),
                        BigDecimal.valueOf(day.threshold()), (long) day.slotCount(), null, day.date(), null), refs));
            }
        }
        if (sections.contains(ReportSection.TAGS)) {
            for (var frequency : statistics.tagFrequencies()) {
                var refs = data.tags().stream().filter(tag -> tag.observed() && tag.getLabelCode() == frequency.code()
                        && !tag.getAppliesToLocalDate().isBefore(data.selection().from()) && !tag.getAppliesToLocalDate().isAfter(data.selection().to()))
                        .map(tag -> new SourceRefResponse("TAG", tag.getId(), tag.getVersion(), tag.getRevision(), tag.getAppliesToLocalDate())).toList();
                result.add(fact(FactType.TAG_FREQUENCY, new FactValueResponse(frequency.code().name(), null, null,
                        frequency.occurrences(), frequency.days(), null, null), refs));
            }
            for (var tag : data.tags().stream().filter(t -> t.observed()
                    && !t.getAppliesToLocalDate().isBefore(data.selection().from()) && !t.getAppliesToLocalDate().isAfter(data.selection().to())).limit(100).toList()) {
                result.add(fact(FactType.QUOTE, new FactValueResponse(tag.getLabelCode().name(), null, null, null, null,
                        tag.getAppliesToLocalDate(), tag.getQuote()), List.of(new SourceRefResponse("ENTRY", tag.getEntryId(), null,
                        tag.getRevision(), tag.getAppliesToLocalDate()), new SourceRefResponse("TAG", tag.getId(), tag.getVersion(),
                        tag.getRevision(), tag.getAppliesToLocalDate()))));
            }
        }
        for (var medication : statistics.medicationAdherence()) {
            var refs = new ArrayList<SourceRefResponse>();
            data.medications().stream().filter(value -> value.getId().equals(medication.medicationId()))
                    .forEach(value -> refs.add(new SourceRefResponse("MEDICATION", value.getId(), value.getVersion(), null, null)));
            data.occurrences().stream().filter(o -> o.medicationId().equals(medication.medicationId()))
                    .map(o -> new SourceRefResponse("SCHEDULE_SLOT", o.scheduleSlotId(), null, null,
                            o.plannedAt().atZone(ZoneId.of(o.timeZone())).toLocalDate())).distinct().forEach(refs::add);
            data.intakes().stream().filter(value -> value.getMedicationId().equals(medication.medicationId()))
                    .forEach(value -> refs.add(new SourceRefResponse("INTAKE", value.getId(), value.getVersion(), null, null)));
            result.add(fact(FactType.ADHERENCE, new FactValueResponse(medication.medicationName(), medication.takenRate(),
                    medication.markedCoverage(), medication.taken(), medication.planned(), null, null), refs));
        }
        for (var comparison : comparisons) {
            var refs = data.ratings().stream().filter(r -> comparison.withMention().dates().contains(r.getLocalDate())
                    || comparison.withoutMention().dates().contains(r.getLocalDate()))
                    .map(r -> new SourceRefResponse("RATING", r.getId(), r.getVersion(), null, r.getLocalDate())).toList();
            var context = data.tags().stream().filter(tag -> tag.getLabelCode() == comparison.tagCode())
                    .map(tag -> new SourceRefResponse("TAG", tag.getId(), tag.getVersion(), tag.getRevision(), tag.getAppliesToLocalDate())).toList();
            var sources = new ArrayList<>(refs);
            sources.addAll(context);
            result.add(fact(FactType.COMPARISON, new FactValueResponse(comparison.tagCode() + ":" + comparison.metric(),
                    comparison.withMention().mean(), comparison.withoutMention().mean(), (long) comparison.withMention().nDays(),
                    (long) comparison.withoutMention().nDays(), null, comparison.status()), List.copyOf(sources)));
        }
        for (var ambiguity : statistics.ambiguousContext()) {
            result.add(fact(FactType.AMBIGUITY, new FactValueResponse(ambiguity.codes().toString(), null, null,
                    null, null, ambiguity.date(), null), data.tags().stream().filter(tag -> ambiguity.date().equals(tag.getAppliesToLocalDate()))
                    .map(tag -> new SourceRefResponse("TAG", tag.getId(), tag.getVersion(), tag.getRevision(), tag.getAppliesToLocalDate())).toList()));
        }
        return List.copyOf(result);
    }

    private FactResponse fact(
            FactType type,
            FactValueResponse value,
            List<SourceRefResponse> refs
    ) {
        String hash = Hashing.sha256(type + "|" + json.writeValueAsString(value) + "|" + json.writeValueAsString(refs));
        return new FactResponse("f-" + hash, type, value, List.copyOf(refs));
    }
}
