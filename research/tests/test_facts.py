from study.facts import make_value, sentence

D = "2026-10-05"
# Ожидаемые строки получены запуском TemplateOverview.sentence из backend на тех же значениях.
GOLDEN = [
    ("COVERAGE", make_value("ANXIETY", count=30, total=42), "Тревога: заполнено 30 из 42 слотов."),
    ("METRIC_MEAN", make_value("MOOD", "5.5000", count=12), "Настроение: среднее доступных дневных значений 5.5/10, дней с данными 12."),
    ("METRIC_MEAN", make_value("ENERGY", count=0), "Энергия: нет оценок за период."),
    ("HIGH_ANXIETY", make_value("ANXIETY", "8.3333", "7", 3, date=D),
     "2026-10-05: средняя тревога 8.3333/10 по 3 слотам; достигнут порог интерфейса 7."),
    ("TAG_FREQUENCY", make_value("SLEEP_DEFICIT", count=6, total=5), "Недостаток сна: подтверждённых присутствующих упоминаний 6, дней 5."),
    ("ADHERENCE", make_value("Препарат А", "0.5000", "0.7500", 7, 14),
     "Препарат А: подтверждено принятыми 7 из 14 плановых доз (50%); покрытие отметками 75%."),
    ("ADHERENCE", make_value("Препарат Б", count=0, total=0), "Препарат Б: плановых доз за период нет."),
    ("COMPARISON", make_value("SLEEP_DEFICIT:ANXIETY", "7.2000", "5.1000", 6, 9, quote="SUFFICIENT"),
     "Недостаток сна: тревога в дни с подтверждённым упоминанием выше в дни без такого упоминания: 7.2 и 5.1/10; "
     "дней 6 и 9. Это связь, а не доказанная причина."),
    ("COMPARISON", make_value("CAFFEINE:MOOD", "5", "5", 5, 5, quote="SUFFICIENT"),
     "Кофеин: настроение в дни с подтверждённым упоминанием совпадает со значением в дни без такого упоминания: "
     "5 и 5/10; дней 5 и 5. Это связь, а не доказанная причина."),
    ("COMPARISON", make_value("CAFFEINE:ENERGY", count=4, total=8, quote="INSUFFICIENT_DATA"),
     "Кофеин: недостаточно проверенных дней для сравнения энергия (с упоминанием 4, без упоминания 8)."),
    ("QUOTE", make_value("SLEEP_DEFICIT", date=D, quote="не спал"), "2026-10-05: подтверждённая цитата «не спал»."),
    ("AMBIGUITY", make_value("[SLEEP_DEFICIT]", date=D),
     "2026-10-05: неоднозначный контекст [SLEEP_DEFICIT]; день исключён из соответствующих сравнений."),
]


def test_sentences_match_backend_template():
    for kind, value, expected in GOLDEN:
        assert sentence({"type": kind, "value": value}) == expected
