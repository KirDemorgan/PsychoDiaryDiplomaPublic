package alex.drankov.diary.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FactValueResponse(
        String label,
        BigDecimal value,
        BigDecimal referenceValue,
        Long count,
        Long total,
        LocalDate date,
        String quote
) {
}
