package alex.drankov.diary.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyValueResponse(
        LocalDate date,
        BigDecimal anxietyMean,
        int anxietySlotCount,
        BigDecimal moodMean,
        int moodSlotCount,
        BigDecimal energyMean,
        int energySlotCount
) {
}
