from __future__ import annotations

from typing import Any, Dict, List, Set

LABEL_RU = {
    "SLEEP_DEFICIT": "Недостаток сна",
    "WORK_STUDY": "Работа и учёба",
    "SOCIAL_EVENT": "Социальное событие",
    "CONFLICT": "Конфликт",
    "PHYSICAL_ACTIVITY": "Физическая активность",
    "CAFFEINE": "Кофеин",
    "ANXIETY": "Тревога",
    "CALM": "Спокойствие",
    "FATIGUE": "Усталость",
    "NAUSEA": "Тошнота",
    "HEADACHE": "Головная боль",
    "TREMOR": "Дрожь",
}
LABELS: List[str] = list(LABEL_RU)
PRESENCE = ["PRESENT", "NEGATED", "UNCERTAIN"]
TEMPORAL = ["CURRENT", "PAST", "FUTURE", "UNSPECIFIED"]
SUBJECT = ["SELF", "OTHER", "UNSPECIFIED"]


def make_event(label: str, quote: str, presence: str = "PRESENT", temporal: str = "CURRENT",
               subject: str = "SELF", occurrence: int = 0) -> Dict[str, Any]:
    return {"labelCode": label, "quote": quote, "occurrence": occurrence,
            "presence": presence, "temporal": temporal, "subject": subject}


def is_actual(event: Dict[str, Any]) -> bool:
    """Признак, который попадает в аналитику: присутствует, касается пользователя, относится к текущему дню."""
    return (event["presence"], event["temporal"], event["subject"]) == ("PRESENT", "CURRENT", "SELF")


def actual_labels(events: List[Dict[str, Any]]) -> Set[str]:
    return {event["labelCode"] for event in events if is_actual(event)}
