package alex.drankov.diary.analytics.dto;

import alex.drankov.diary.analytics.entity.Metric;
import alex.drankov.diary.tags.entity.TagCode;

import java.math.BigDecimal;
import java.util.List;

public record ComparisonResponse(
        TagCode tagCode,
        Metric metric,
        PeriodResponse period,
        String status,
        ComparisonGroupResponse withMention,
        ComparisonGroupResponse withoutMention,
        ComparisonExcludedResponse excluded,
        BigDecimal delta,
        List<String> limitations,
        long dataRevision
) {
}
