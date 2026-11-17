package alex.drankov.diary.tags.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TagCode {
    SLEEP_DEFICIT("Недостаток сна", "CONTEXT"),
    WORK_STUDY("Работа и учёба", "CONTEXT"),
    SOCIAL_EVENT("Социальное событие", "CONTEXT"),
    CONFLICT("Конфликт", "CONTEXT"),
    PHYSICAL_ACTIVITY("Физическая активность", "CONTEXT"),
    CAFFEINE("Кофеин", "CONTEXT"),
    ANXIETY("Тревога", "STATE"),
    CALM("Спокойствие", "STATE"),
    FATIGUE("Усталость", "STATE"),
    NAUSEA("Тошнота", "SYMPTOM"),
    HEADACHE("Головная боль", "SYMPTOM"),
    TREMOR("Дрожь", "SYMPTOM");

    private final String labelRu;
    private final String category;
}
