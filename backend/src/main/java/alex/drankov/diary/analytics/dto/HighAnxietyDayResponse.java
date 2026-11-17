package alex.drankov.diary.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HighAnxietyDayResponse(
        LocalDate date,
        BigDecimal mean,
        int slotCount,
        int threshold,
        String rule
) {
}
