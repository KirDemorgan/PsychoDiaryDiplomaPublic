import pytest

from study.facts import make_fact, make_value
from study.narrative_guard import Rejected, verify

MEAN = make_fact("METRIC_MEAN", make_value("ANXIETY", "7.2", count=6), [])
HIGH = make_fact("HIGH_ANXIETY", make_value("ANXIETY", "8", "7", 3, date="2026-10-05"), [])
QUOTE = make_fact("QUOTE", make_value("SLEEP_DEFICIT", date="2026-10-05", quote="плохо спал"), [])

# Те же примеры, что в NarrativeGuardTest (backend).
ACCEPTED = [
    "Средняя тревога 7,2 из 10, дней с данными 6.",
    "Средняя тревога 7.2/10 за 6 дней.",
    "5 октября (05.10.2026) средняя тревога достигла 8 из 10 по 3 слотам.",
    "Тревога выше порога интерфейса 7 из 10 в один из дней.",
    "Это связь, а не доказанная причина.",
    "5 октября 2026 года средняя тревога достигла 8 из 10 по 3 слотам.",
    "5 октября средняя тревога достигла 8 из 10 по 3 слотам.",
    "С 5 по 5 октября 2026 тревога была 8 из 10.",
    "Сравнение невозможно из-за отсутствия данных.",
    "Сравнение невозможно из-за недостатка проверенных дней.",
]
REJECTED = [
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
    "Тревога выше из-за отсутствия сна.",
]


@pytest.mark.parametrize("text", ACCEPTED)
def test_accepts(text):
    verify(text, [MEAN, HIGH])


@pytest.mark.parametrize("text", REJECTED)
def test_rejects(text):
    with pytest.raises(Rejected):
        verify(text, [MEAN, HIGH])


def test_quotation_only_claim_is_rejected():
    with pytest.raises(Rejected, match="quotations"):
        verify("«плохо спал»", [QUOTE])


def test_verbatim_quotation_with_calculated_fact_is_accepted():
    verify("Среднее 7,2; 2026-10-05: «плохо спал».", [MEAN, QUOTE])
    with pytest.raises(Rejected):
        verify("Среднее 7,2; «не ел».", [MEAN, QUOTE])
