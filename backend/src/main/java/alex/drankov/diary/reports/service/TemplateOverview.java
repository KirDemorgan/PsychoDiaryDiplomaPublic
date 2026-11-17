package alex.drankov.diary.reports.service;

import alex.drankov.diary.reports.dto.FactResponse;
import alex.drankov.diary.reports.dto.FactValueResponse;
import alex.drankov.diary.reports.dto.OverviewClaimResponse;
import alex.drankov.diary.reports.dto.OverviewProvenanceResponse;
import alex.drankov.diary.reports.dto.ReportOverviewResponse;
import alex.drankov.diary.reports.entity.FactType;
import alex.drankov.diary.reports.entity.OverviewMode;
import alex.drankov.diary.tags.entity.TagCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class TemplateOverview {
    public ReportOverviewResponse build(List<FactResponse> facts) {
        var claims = facts.stream().filter(fact -> fact.type() != FactType.QUOTE)
                .map(fact -> new OverviewClaimResponse("c-" + fact.factId().substring(2), sentence(fact),
                        List.of(fact.factId()), fact.sourceRefs())).toList();
        return new ReportOverviewResponse("AI-обзор наблюдений для обсуждения с врачом", claims,
                List.of("Детерминированный шаблон: текст не сгенерирован нейросетью", "Обзор не является медицинским заключением",
                        "Связи не доказывают причинность; эффективность лечения не оценивается", "Числа и даты рассчитаны сервером"),
                new OverviewProvenanceResponse(OverviewMode.TEMPLATE, null, "diary-template-v1", "report-facts-v1"));
    }

    public String sentence(FactResponse fact) {
        var value = fact.value();
        return switch (fact.type()) {
            case COVERAGE -> metric(value.label()) + ": заполнено " + value.count() + " из " + value.total() + " слотов.";
            case METRIC_MEAN -> value.value() == null ? metric(value.label()) + ": нет оценок за период."
                    : metric(value.label()) + ": среднее доступных дневных значений " + number(value.value())
                    + "/10, дней с данными " + value.count() + ".";
            case HIGH_ANXIETY -> value.date() + ": средняя тревога " + number(value.value()) + "/10 по " + value.count()
                    + " слотам; достигнут порог интерфейса " + number(value.referenceValue()) + ".";
            case TAG_FREQUENCY -> label(value.label()) + ": подтверждённых присутствующих упоминаний " + value.count()
                    + ", дней " + value.total() + ".";
            case ADHERENCE -> value.total() == 0 ? value.label() + ": плановых доз за период нет."
                    : value.label() + ": подтверждено принятыми " + value.count() + " из " + value.total()
                    + " плановых доз (" + number(value.value().multiply(BigDecimal.valueOf(100)))
                    + "%); покрытие отметками " + number(value.referenceValue().multiply(BigDecimal.valueOf(100))) + "%.";
            case COMPARISON -> comparison(value);
            case QUOTE -> value.date() + ": подтверждённая цитата «" + value.quote() + "».";
            case AMBIGUITY -> value.date() + ": неоднозначный контекст " + value.label() + "; день исключён из соответствующих сравнений.";
        };
    }

    private String comparison(FactValueResponse value) {
        String[] parts = value.label().split(":");
        String name = label(parts[0]);
        String metric = metric(parts[1]);
        if (!"SUFFICIENT".equals(value.quote())) {
            return name + ": недостаточно проверенных дней для сравнения " + metric.toLowerCase(Locale.ROOT)
                    + " (с упоминанием " + value.count() + ", без упоминания " + value.total() + ").";
        }
        int difference = value.value().compareTo(value.referenceValue());
        return name + ": " + metric.toLowerCase(Locale.ROOT) + " в дни с подтверждённым упоминанием "
                + (difference > 0 ? "выше" : difference < 0 ? "ниже" : "совпадает со значением")
                + " в дни без такого упоминания: " + number(value.value()) + " и " + number(value.referenceValue())
                + "/10; дней " + value.count() + " и " + value.total() + ". Это связь, а не доказанная причина.";
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String metric(String code) {
        return switch (code) {
            case "ANXIETY" -> "Тревога";
            case "MOOD" -> "Настроение";
            case "ENERGY" -> "Энергия";
            default -> throw new IllegalArgumentException("Unknown metric");
        };
    }

    private static String label(String code) {
        return TagCode.valueOf(code).getLabelRu();
    }
}
