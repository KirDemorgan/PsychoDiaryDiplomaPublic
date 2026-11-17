from __future__ import annotations

import re
from datetime import date
from typing import Any, Dict, List, Set

from .facts import sentence

# Порт NarrativeGuard из backend. Соответствие поддерживается набором одинаковых тест-кейсов (tests/test_narrative_guard.py).
LETTER = r"[^\W\d_]"
FLAGS = re.IGNORECASE | re.UNICODE
ISO_DATE = re.compile(r"\b\d{4}-\d{2}-\d{2}\b")
DOTTED_DATE = re.compile(r"\b(\d{1,2})\.(\d{1,2})\.(\d{4})\b")
NUMBER = re.compile(r"\d+(?:[.,]\d+)?")
QUOTATION = re.compile(r"«([^»]*)»")
NUMBER_WORD = re.compile(
    rf"(?<!{LETTER})(?:ноль|нуль|два|две|двух|три|трех|трёх|четыре|четырех|четырёх|пять|пяти|шесть|шести|семь|семи"
    rf"|восемь|восьми|девять|девяти|десять|десяти|половин{LETTER}*)(?!{LETTER})", FLAGS)
FORBIDDEN = re.compile(
    rf"(?<!{LETTER})(?:диагноз{LETTER}*|диагностир{LETTER}*|расстройств{LETTER}*|депресс{LETTER}*|заболевани{LETTER}*"
    rf"|болезн{LETTER}*|причин{LETTER}*|вследствие|из-за|обусловлен{LETTER}*|приводит{LETTER}*|приводил{LETTER}*"
    rf"|привел{LETTER}*|привело|вызыва{LETTER}*|вызвал{LETTER}*|вызван{LETTER}*|благодаря|лечени{LETTER}*|терапи{LETTER}*"
    rf"|рекоменд{LETTER}*|следует|необходим{LETTER}*|нужно|эффективн{LETTER}*|помога{LETTER}*|помог{LETTER}*"
    rf"|доказ{LETTER}*|увеличить|уменьшить|отменить|назначить|прогноз{LETTER}*)(?!{LETTER})", FLAGS)
MONTHS = {"января": 1, "февраля": 2, "марта": 3, "апреля": 4, "мая": 5, "июня": 6, "июля": 7, "августа": 8,
          "сентября": 9, "октября": 10, "ноября": 11, "декабря": 12}
WORD_DATE = re.compile(
    rf"(?<!\d)(\d{{1,2}})\s+({'|'.join(MONTHS)})(?!{LETTER})(?:\s+(\d{{4}})(?:\s+года(?!{LETTER}))?)?", FLAGS)
NO_DATA_REASON = re.compile(
    r"из-за (?:отсутствия|недостатка) (?:достаточного количества )?(?:проверенных )?(?:данных|дней)", FLAGS)
DISCLAIMERS = ["Это связь, а не доказанная причина.", "связь, а не причина", "не доказывает причинность",
               "не доказывает причинности"]


class Rejected(ValueError):
    pass


def _parse_dates(text: str) -> Set[date]:
    result = set()
    try:
        for match in ISO_DATE.finditer(text):
            result.add(date.fromisoformat(match.group()))
        for match in DOTTED_DATE.finditer(text):
            result.add(date(int(match.group(3)), int(match.group(2)), int(match.group(1))))
    except ValueError as error:
        raise Rejected("Invalid date") from error
    return result


def _strip_dates(text: str) -> str:
    return WORD_DATE.sub(" ", DOTTED_DATE.sub(" ", ISO_DATE.sub(" ", text)))


def _verify_word_dates(text: str, allowed: Set[date]) -> None:
    for match in WORD_DATE.finditer(text):
        day, month = int(match.group(1)), MONTHS[match.group(2).lower()]
        if match.group(3) is None:
            if not any(item.day == day and item.month == month for item in allowed):
                raise Rejected("Unsupported date")
            continue
        try:
            claimed = date(int(match.group(3)), month, day)
        except ValueError as error:
            raise Rejected("Invalid date") from error
        if claimed not in allowed:
            raise Rejected("Unsupported date")


def _numbers(text: str) -> Set[str]:
    from decimal import Decimal
    result = set()
    for match in NUMBER.finditer(text):
        value = Decimal(match.group().replace(",", ".")).normalize()
        result.add(format(value, "f") if value != 0 else "0")
    return result


def verify(text: str, cited: List[Dict[str, Any]]) -> None:
    if all(fact["type"] == "QUOTE" for fact in cited):
        raise Rejected("Claim cites only quotations")
    cleaned = text
    for disclaimer in DISCLAIMERS:
        cleaned = cleaned.replace(disclaimer, " ")
    cleaned = NO_DATA_REASON.sub(" ", cleaned)
    if FORBIDDEN.search(cleaned):
        raise Rejected("Forbidden wording")
    quotes, numbers, dates = set(), set(), set()
    for fact in cited:
        if fact["type"] == "QUOTE":
            if fact["value"].get("quote") is not None:
                quotes.add(fact["value"]["quote"])
            if fact["value"].get("date"):
                dates.add(date.fromisoformat(fact["value"]["date"]))
            continue
        allowed = sentence(fact)
        dates |= _parse_dates(allowed)
        numbers |= _numbers(_strip_dates(allowed))
    for item in dates:
        numbers |= {str(item.day), str(item.month), str(item.year)}
    for match in QUOTATION.finditer(text):
        if match.group(1) not in quotes:
            raise Rejected("Unverified quotation")
    plain = QUOTATION.sub(" ", text)
    if NUMBER_WORD.search(plain):
        raise Rejected("Spelled-out number")
    if not dates >= _parse_dates(plain):
        raise Rejected("Unsupported date")
    _verify_word_dates(plain, dates)
    if not numbers >= _numbers(_strip_dates(plain)):
        raise Rejected("Unsupported number")
