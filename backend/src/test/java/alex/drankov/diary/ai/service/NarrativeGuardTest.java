package alex.drankov.diary.ai.service;

import alex.drankov.diary.reports.dto.FactResponse;
import alex.drankov.diary.reports.dto.FactValueResponse;
import alex.drankov.diary.reports.entity.FactType;
import alex.drankov.diary.reports.service.TemplateOverview;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrativeGuardTest {
    private final TemplateOverview template = new TemplateOverview();
    private final FactResponse mean = new FactResponse("f-mean", FactType.METRIC_MEAN,
            new FactValueResponse("ANXIETY", new BigDecimal("7.2"), null, 6L, null, null, null), List.of());
    private final FactResponse high = new FactResponse("f-high", FactType.HIGH_ANXIETY,
            new FactValueResponse(null, new BigDecimal("8"), new BigDecimal("7"), 3L, null, LocalDate.of(2026, 10, 5), null), List.of());

    @ParameterizedTest(name = "принимается: {0}")
    @ValueSource(strings = {
            "Средняя тревога 7,2 из 10, дней с данными 6.",
            "Средняя тревога 7.2/10 за 6 дней.",
            "5 октября (05.10.2026) средняя тревога достигла 8 из 10 по 3 слотам.",
            "Тревога выше порога интерфейса 7 из 10 в один из дней.",
            "Это связь, а не доказанная причина.",
            "5 октября 2026 года средняя тревога достигла 8 из 10 по 3 слотам.",
            "5 октября средняя тревога достигла 8 из 10 по 3 слотам.",
            "С 5 по 5 октября 2026 тревога была 8 из 10.",
            "Сравнение невозможно из-за отсутствия данных.",
            "Сравнение невозможно из-за недостатка проверенных дней."
    })
    @DisplayName("Текст с числами и датами из цитируемых фактов и допустимой оговоркой проходит проверку")
    void accepts(String text) {
        assertThatCode(() -> NarrativeGuard.verify(text, List.of(mean, high), template::sentence)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "отклоняется: {0}")
    @ValueSource(strings = {
            "Средняя тревога 7,3 из 10.",
            "Тревога выросла на 0,8 пункта.",
            "Средняя тревога около семи.",
            "Это 06.10.2026, средняя тревога 7,2.",
            "Тревога связана с недосыпом, поэтому нужно лечение.",
            "Препарат помог снизить тревогу.",
            "Рекомендуется обсудить дозу.",
            "6 октября средняя тревога достигла 8 из 10.",
            "5 ноября средняя тревога достигла 8 из 10.",
            "5 октября 2025 года тревога была 8 из 10.",
            "30 февраля 2026 года тревога была 8 из 10.",
            "Тревога выше из-за недосыпа.",
            "Тревога выше из-за отсутствия сна."
    })
    @DisplayName("Выдуманные числа, даты, слова-числа, причинность, лечение и рекомендации отклоняются")
    void rejects(String text) {
        assertThatThrownBy(() -> NarrativeGuard.verify(text, List.of(mean, high), template::sentence))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Пункт, который ссылается только на цитаты, отклоняется")
    void rejectsQuotationOnlyClaim() {
        var quote = new FactResponse("f-quote", FactType.QUOTE,
                new FactValueResponse(null, null, null, null, null, LocalDate.of(2026, 10, 5), "плохо спал"), List.of());
        assertThatThrownBy(() -> NarrativeGuard.verify("«плохо спал»", List.of(quote), template::sentence))
                .hasMessageContaining("quotations");
    }
}
