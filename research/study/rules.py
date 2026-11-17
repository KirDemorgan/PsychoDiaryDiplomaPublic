from __future__ import annotations

import re
from typing import Any, Dict, List

from .labels import make_event

LEXICON = {
    "SLEEP_DEFICIT": [r"не хватило сна", r"не высп\w*", r"мало спал\w*", r"плохо спал\w*", r"бессонн\w*"],
    "WORK_STUDY": [r"уч[её]б\w*", r"работ\w*", r"экзамен\w*", r"дедлайн\w*"],
    "SOCIAL_EVENT": [r"встрет\w* с друз\w*", r"вечеринк\w*", r"в гостях", r"гулял\w* с друз\w*"],
    "CONFLICT": [r"поссор\w*", r"конфликт\w*", r"поругал\w*", r"ссор\w*"],
    "PHYSICAL_ACTIVITY": [r"физкультур\w*", r"бегать", r"бегал\w*", r"трениров\w*", r"спортзал\w*"],
    "CAFFEINE": [r"кофе\w*", r"энергетик\w*"],
    "ANXIETY": [r"тревог\w*", r"тревож\w*", r"волнов\w*"],
    "CALM": [r"спокой\w*", r"спокоен"],
    "FATIGUE": [r"устал\w*", r"усталость", r"вымотал\w*"],
    "NAUSEA": [r"тошн\w*"],
    "HEADACHE": [r"бол\w* голов\w*", r"голов\w* бол\w*"],
    "TREMOR": [r"дрож\w*", r"тряс\w*"],
}

FUTURE = re.compile(r"завтра|будет|на следующ")
PAST = re.compile(r"вчера|назад|раньше|позавчера")
CURRENT = re.compile(r"сегодня|сейчас|утром|днём|вечером")
OTHER = re.compile(r"сестр|брат|мама|мам[ыу]|знакомый|друг сказал|коллега сказал")
UNCERTAIN = re.compile(r"вдруг|может|пожалуй|боюсь|опасаюсь|не могу понять|не знаю")
SENTENCE_NEGATION = re.compile(r"неверно|этого не было|не было")


def extract(text: str) -> Dict[str, List[Dict[str, Any]]]:
    """Baseline: словарь и локальные правила отрицания, времени и субъекта. Эталон не используется."""
    found = []
    for match in re.finditer(r"[^.!?;\n]+", text):
        quote = match.group().strip()
        if not quote:
            continue
        lower = quote.lower()
        temporal = ("FUTURE" if FUTURE.search(lower) else "PAST" if PAST.search(lower)
                    else "CURRENT" if CURRENT.search(lower) else "UNSPECIFIED")
        subject = "OTHER" if OTHER.search(lower) else "SELF"
        uncertain = bool(UNCERTAIN.search(lower))
        for label, patterns in LEXICON.items():
            hit = next((p for p in patterns if re.search(p, lower)), None)
            if hit is None:
                continue
            negated = bool(SENTENCE_NEGATION.search(lower)) or any(
                re.search(r"\bне\s+(?:\w+\s+){0,2}(?:" + pattern + r")", lower) for pattern in patterns)
            if label == "SLEEP_DEFICIT" and re.search(r"не хватило сна|не высп|мало спал|плохо спал", lower):
                negated = bool(SENTENCE_NEGATION.search(lower)) and "не хватило" not in lower
            presence = "UNCERTAIN" if uncertain else "NEGATED" if negated else "PRESENT"
            occurrence = text[: match.start()].count(quote)
            found.append(make_event(label, quote, presence, temporal, subject, occurrence))
    return {"candidates": found}
