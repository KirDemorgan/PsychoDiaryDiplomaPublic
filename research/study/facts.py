from __future__ import annotations

import hashlib
import json
import uuid
from decimal import ROUND_HALF_UP, Decimal
from typing import Any, Dict, List, Optional

from .labels import LABEL_RU

METRIC_RU = {"ANXIETY": "Тревога", "MOOD": "Настроение", "ENERGY": "Энергия"}
SUFFICIENT = "SUFFICIENT"
HIGH_ANXIETY_THRESHOLD = Decimal(7)


def num(value) -> str:
    """Как number() в TemplateOverview: без хвостовых нулей и без экспоненты."""
    text = format(Decimal(str(value)).normalize(), "f")
    return "0" if text in ("-0", "") else text


def sentence(fact: Dict[str, Any]) -> str:
    """Порт TemplateOverview.sentence из backend. Совпадение проверяется golden-тестами (tests/test_facts.py)."""
    value, kind = fact["value"], fact["type"]
    if kind == "COVERAGE":
        return f"{METRIC_RU[value['label']]}: заполнено {value['count']} из {value['total']} слотов."
    if kind == "METRIC_MEAN":
        if value["value"] is None:
            return f"{METRIC_RU[value['label']]}: нет оценок за период."
        return (f"{METRIC_RU[value['label']]}: среднее доступных дневных значений {num(value['value'])}/10, "
                f"дней с данными {value['count']}.")
    if kind == "HIGH_ANXIETY":
        return (f"{value['date']}: средняя тревога {num(value['value'])}/10 по {value['count']} слотам; "
                f"достигнут порог интерфейса {num(value['referenceValue'])}.")
    if kind == "TAG_FREQUENCY":
        return (f"{LABEL_RU[value['label']]}: подтверждённых присутствующих упоминаний {value['count']}, "
                f"дней {value['total']}.")
    if kind == "ADHERENCE":
        if value["total"] == 0:
            return f"{value['label']}: плановых доз за период нет."
        return (f"{value['label']}: подтверждено принятыми {value['count']} из {value['total']} плановых доз "
                f"({num(Decimal(str(value['value'])) * 100)}%); покрытие отметками "
                f"{num(Decimal(str(value['referenceValue'])) * 100)}%.")
    if kind == "COMPARISON":
        tag, metric = value["label"].split(":")
        name, metric_name = LABEL_RU[tag], METRIC_RU[metric].lower()
        if value["quote"] != SUFFICIENT:
            return (f"{name}: недостаточно проверенных дней для сравнения {metric_name} "
                    f"(с упоминанием {value['count']}, без упоминания {value['total']}).")
        difference = Decimal(str(value["value"])) - Decimal(str(value["referenceValue"]))
        word = "выше" if difference > 0 else "ниже" if difference < 0 else "совпадает со значением"
        return (f"{name}: {metric_name} в дни с подтверждённым упоминанием {word} в дни без такого упоминания: "
                f"{num(value['value'])} и {num(value['referenceValue'])}/10; дней {value['count']} и {value['total']}. "
                "Это связь, а не доказанная причина.")
    if kind == "QUOTE":
        return f"{value['date']}: подтверждённая цитата «{value['quote']}»."
    if kind == "AMBIGUITY":
        return f"{value['date']}: неоднозначный контекст {value['label']}; день исключён из соответствующих сравнений."
    raise ValueError(f"Неизвестный тип факта {kind}")


def make_value(label=None, value=None, reference=None, count=None, total=None, date=None, quote=None) -> Dict[str, Any]:
    return {"label": label, "value": None if value is None else str(value),
            "referenceValue": None if reference is None else str(reference), "count": count, "total": total,
            "date": date, "quote": quote}


def source_ref(kind: str, identity: str, date: Optional[str] = None, revision: Optional[int] = None) -> Dict[str, Any]:
    return {"type": kind, "id": str(uuid.uuid5(uuid.NAMESPACE_URL, f"{kind}:{identity}")), "version": 1,
            "revision": revision, "date": date}


def make_fact(kind: str, value: Dict[str, Any], refs: List[Dict[str, Any]]) -> Dict[str, Any]:
    digest = hashlib.sha256((kind + "|" + json.dumps(value, ensure_ascii=False, sort_keys=True) + "|"
                             + json.dumps(refs, ensure_ascii=False, sort_keys=True)).encode()).hexdigest()
    return {"factId": "f-" + digest, "type": kind, "value": value, "sourceRefs": refs}


def alias(index: int) -> str:
    return f"F{index + 1}"


def alias_map(facts: List[Dict[str, Any]]) -> Dict[str, Dict[str, Any]]:
    return {alias(i): fact for i, fact in enumerate(facts)}


def model_input(facts: List[Dict[str, Any]]) -> str:
    """Вход модели как в AiQueue: короткий id, тип, значение и разрешённый текст (для цитат пустой)."""
    return json.dumps([{"id": alias(i), "type": fact["type"], "value": fact["value"],
                        "allowedText": "" if fact["type"] == "QUOTE" else sentence(fact)}
                       for i, fact in enumerate(facts)], ensure_ascii=False)


def mean4(values: List[Decimal]) -> Optional[Decimal]:
    if not values:
        return None
    return (sum(values, Decimal(0)) / len(values)).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)
