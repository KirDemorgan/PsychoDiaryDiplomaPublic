package alex.drankov.diary.common.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class TimeRules {
    private TimeRules() {
    }

    public static ZoneId zone(String value) {
        if (value == null || !ZoneId.getAvailableZoneIds().contains(value)) {
            throw ApiException.invalid("Укажите IANA-часовой пояс");
        }
        return ZoneId.of(value);
    }

    public static void occurrence(OffsetDateTime value, String timeZone) {
        var zone = zone(timeZone);
        if (value == null || !zone.getRules().getOffset(value.toInstant()).equals(value.getOffset())) {
            throw ApiException.invalid("Offset времени не соответствует часовому поясу");
        }
    }

    public static void range(LocalDate from, LocalDate to, int maxDays) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= maxDays) {
            throw ApiException.invalid("Некорректный диапазон дат");
        }
    }
}
