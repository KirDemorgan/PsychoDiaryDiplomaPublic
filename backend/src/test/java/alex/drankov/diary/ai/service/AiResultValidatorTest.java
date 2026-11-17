package alex.drankov.diary.ai.service;

import alex.drankov.diary.ai.dto.ModelCandidate;
import alex.drankov.diary.ai.dto.ModelExtraction;
import alex.drankov.diary.ai.dto.ModelNarrative;
import alex.drankov.diary.ai.dto.ModelNarrativeClaim;
import alex.drankov.diary.reports.dto.FactResponse;
import alex.drankov.diary.reports.dto.FactValueResponse;
import alex.drankov.diary.reports.dto.SourceRefResponse;
import alex.drankov.diary.reports.entity.FactType;
import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.reports.service.TemplateOverview;
import alex.drankov.diary.tags.entity.TagCode;
import alex.drankov.diary.tags.entity.TagPresence;
import alex.drankov.diary.tags.entity.TagSubject;
import alex.drankov.diary.tags.entity.TagTemporal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResultValidatorTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final TemplateOverview template = new TemplateOverview();
    private final AiResultValidator validator = new AiResultValidator(json, template);

    @Test
    @DisplayName("Позиции цитат вычисляются в UTF-16; отрицание и будущее сохраняются без превращения в подтверждённый факт")
    void utf16AndContext() {
        String text = "😀 тошноты нет; раньше тошноты не было";
        var model = new ModelCandidate(TagCode.NAUSEA, "тошноты", 1, TagPresence.NEGATED, TagTemporal.PAST, TagSubject.SELF);
        var result = validator.extraction(text, json.writeValueAsString(new ModelExtraction(List.of(model))));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().start()).isEqualTo(text.lastIndexOf("тошноты"));
        assertThat(text.substring(result.getFirst().start(), result.getFirst().end())).isEqualTo("тошноты");
        assertThat(result.getFirst().presence()).isEqualTo(TagPresence.NEGATED);
        assertThat(result.getFirst().temporal()).isEqualTo(TagTemporal.PAST);
        assertThat(validator.extraction(text, "{\"candidates\":[]}")).isEmpty();
    }

    @Test
    @DisplayName("Невалидный JSON, неизвестные поля, выдуманная или неоднозначная цитата и половина emoji отклоняются")
    void extractionRejections() {
        assertThatThrownBy(() -> validator.extraction("текст", "not json")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> validator.extraction("текст", "{\"candidates\":[],\"command\":\"delete\"}")).isInstanceOf(RuntimeException.class);
        for (String quote : List.of("выдумано", "тревожно")) {
            var candidate = candidate(quote, null);
            assertThatThrownBy(() -> validator.extraction("тревожно тревожно", json.writeValueAsString(new ModelExtraction(List.of(candidate)))))
                    .isInstanceOf(RuntimeException.class);
        }
        String valid = json.writeValueAsString(new ModelExtraction(List.of(candidate("1", 0))));
        for (String raw : List.of(valid.replace("\"quote\":\"1\"", "\"quote\":1"),
                valid.replace("\"occurrence\":0", "\"occurrence\":\"0\""),
                valid.replace("\"occurrence\":0", "\"occurrence\":0.5"),
                valid.replace("\"presence\":\"PRESENT\"", "\"presence\":0"),
                "{\"candidates\":[],\"candidates\":[]}")) {
            assertThatThrownBy(() -> validator.extraction("1", raw)).isInstanceOf(RuntimeException.class);
        }
        var halfEmoji = candidate("\uD83D", 0);
        assertThatThrownBy(() -> validator.extraction("😀", json.writeValueAsString(new ModelExtraction(List.of(halfEmoji)))))
                .isInstanceOf(RuntimeException.class);
        var duplicate = candidate("тревожно", 0);
        assertThatThrownBy(() -> validator.extraction("тревожно", json.writeValueAsString(new ModelExtraction(List.of(duplicate, duplicate)))))
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> validator.extraction("текст", "x".repeat(65537))).hasMessageContaining("size");
    }

    @Test
    @DisplayName("Свободный обзор принимается, если числа и даты взяты из цитируемых фактов; ссылки на источники вычисляет сервер")
    void narrativeAcceptsGroundedText() {
        var ref = new SourceRefResponse("RATING", UUID.randomUUID(), 0L, null, LocalDate.of(2026, 10, 13));
        var coverage = new FactResponse("f-coverage", FactType.COVERAGE,
                new FactValueResponse("ANXIETY", null, null, 2L, 3L, null, null), List.of(ref));
        var result = validator.narrative(narrative("Тревогу отметили в 2 слотах из 3.", "F1"), List.of(coverage),
                "test-adapter", "report-overview-v2", "report-narrative-v1");
        assertThat(result.provenance().mode()).isEqualTo(OverviewMode.AI);
        assertThat(result.claims()).singleElement().satisfies(claim -> {
            assertThat(claim.text()).isEqualTo("Тревогу отметили в 2 слотах из 3.");
            assertThat(claim.sourceRefs()).containsExactly(ref);
            assertThat(claim.factIds()).containsExactly("f-coverage");
        });
    }

    @Test
    @DisplayName("Свободный обзор отклоняется при выдуманном числе, дате, причине, диагнозе, чужой цитате или неизвестном факте")
    void narrativeRejectsUnsupportedText() {
        var coverage = new FactResponse("f-coverage", FactType.COVERAGE,
                new FactValueResponse("ANXIETY", null, null, 2L, 3L, null, null), List.of());
        var quote = new FactResponse("f-quote", FactType.QUOTE,
                new FactValueResponse(null, null, null, null, null, LocalDate.of(2026, 10, 13), "не спал"), List.of());
        var facts = List.of(coverage, quote);
        for (String text : List.of("Тревогу отметили в 5 слотах из 3.", "Из-за недосыпа тревога выше.", "Похоже на депрессию.",
                "Тревогу отметили в двух слотах.", "Это было 2026-10-14, слотов 2 из 3.", "Цитата: «не ел», слотов 2 из 3.")) {
            assertThatThrownBy(() -> validator.narrative(narrative(text, "F1", "F2"), facts, "test", "p", "s"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> validator.narrative(narrative("Слотов 2 из 3.", "F9"), facts, "test", "p", "s"))
                .hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> validator.narrative(narrative("«не спал»", "F2"), facts, "test", "p", "s"))
                .hasMessageContaining("quotations");
        assertThatThrownBy(() -> validator.narrative("{\"claims\":[]}", facts, "test", "p", "s")).hasMessageContaining("list");
        assertThatThrownBy(() -> validator.narrative("{\"claims\":[{\"text\":\"x\",\"factIds\":[\"F1\"],\"type\":\"COVERAGE\"}]}",
                facts, "test", "p", "s")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Цитата допускается только дословно и только вместе с рассчитанным фактом")
    void narrativeAllowsVerbatimQuotation() {
        var coverage = new FactResponse("f-coverage", FactType.COVERAGE,
                new FactValueResponse("ANXIETY", null, null, 2L, 3L, null, null), List.of());
        var quote = new FactResponse("f-quote", FactType.QUOTE,
                new FactValueResponse(null, null, null, null, null, LocalDate.of(2026, 10, 13), "не спал"), List.of());
        var result = validator.narrative(narrative("Слотов 2 из 3; в записи 2026-10-13: «не спал».", "F1", "F2"),
                List.of(coverage, quote), "test", "p", "s");
        assertThat(result.claims()).hasSize(1);
    }

    @Test
    @DisplayName("Пункт может ссылаться не более чем на восемь фактов")
    void narrativeLimitsFactsPerClaim() {
        var facts = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> new FactResponse("f-" + i, FactType.COVERAGE,
                        new FactValueResponse("ANXIETY", null, null, 2L, 3L, null, null), List.of()))
                .toList();
        var eight = List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8");
        var nine = List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9");
        var accepted = validator.narrative(narrative("Слотов 2 из 3.", eight.toArray(String[]::new)), facts, "test", "p", "s");
        assertThat(accepted.claims().getFirst().factIds()).hasSize(8);
        assertThatThrownBy(() -> validator.narrative(narrative("Слотов 2 из 3.", nine.toArray(String[]::new)), facts, "test", "p", "s"))
                .hasMessageContaining("Unsupported");
    }

    private String narrative(
            String text,
            String... factIds
    ) {
        return json.writeValueAsString(new ModelNarrative(List.of(new ModelNarrativeClaim(text, List.of(factIds)))));
    }

    @Test
    @DisplayName("Номер повторения цитаты игнорируется, если она встречается один раз, и проверяется, если повторяется")
    void extractionToleratesOccurrenceOfUniqueQuote() {
        var once = candidate("тревожно", 3);
        var single = validator.extraction("Сегодня тревожно", json.writeValueAsString(new ModelExtraction(List.of(once))));
        assertThat(single.getFirst().start()).isEqualTo(8);
        var second = candidate("тревожно", 1);
        var repeated = validator.extraction("тревожно и тревожно", json.writeValueAsString(new ModelExtraction(List.of(second))));
        assertThat(repeated.getFirst().start()).isEqualTo(11);
        var beyond = candidate("тревожно", 2);
        assertThatThrownBy(() -> validator.extraction("тревожно и тревожно", json.writeValueAsString(new ModelExtraction(List.of(beyond)))))
                .hasMessageContaining("Unverified");
    }

    private ModelCandidate candidate(
            String quote,
            Integer occurrence
    ) {
        return new ModelCandidate(TagCode.ANXIETY, quote, occurrence, TagPresence.PRESENT, TagTemporal.CURRENT, TagSubject.SELF);
    }
}
