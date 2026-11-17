package alex.drankov.diary.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ComparisonGroupResponse(
        int nDays,
        BigDecimal mean,
        List<LocalDate> dates
) {
}
