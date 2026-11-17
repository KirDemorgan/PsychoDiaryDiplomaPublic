from __future__ import annotations

import re
from collections import defaultdict
from typing import Any, Dict, List, Optional

import numpy as np

from .stats import bootstrap_ci, latency_summary


def normalize(text: str) -> str:
    """Лексическая нормализация для WER: регистр, ё/е, без пунктуации. Отрицания и числа не удаляются."""
    text = text.lower().replace("ё", "е")
    text = re.sub(r"[^0-9a-zа-я]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def _edit_counts(reference: List[str], hypothesis: List[str]) -> Dict[str, int]:
    rows, cols = len(reference), len(hypothesis)
    table = np.zeros((rows + 1, cols + 1), dtype=int)
    table[:, 0] = np.arange(rows + 1)
    table[0, :] = np.arange(cols + 1)
    for i in range(1, rows + 1):
        for j in range(1, cols + 1):
            cost = 0 if reference[i - 1] == hypothesis[j - 1] else 1
            table[i, j] = min(table[i - 1, j] + 1, table[i, j - 1] + 1, table[i - 1, j - 1] + cost)
    return {"errors": int(table[rows, cols]), "words": rows}


def word_errors(reference: str, hypothesis: str) -> Dict[str, int]:
    return _edit_counts(normalize(reference).split(), normalize(hypothesis).split())


def char_errors(reference: str, hypothesis: str) -> Dict[str, int]:
    return _edit_counts(list(normalize(reference).replace(" ", "")), list(normalize(hypothesis).replace(" ", "")))


def phrase_preserved(phrase: str, hypothesis: str) -> bool:
    target = normalize(phrase).split()
    words = normalize(hypothesis).split()
    if not target:
        return True
    return any(words[i:i + len(target)] == target for i in range(len(words) - len(target) + 1))


def _corpus_rate(units: np.ndarray) -> float:
    errors, words = units.sum(axis=0)
    return float(errors / words) if words else float("nan")


def evaluate_asr(gold_rows: List[Dict[str, Any]], runs: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    """WER/CER считаются по всем записям с непустым эталоном; сбой модели — пустая гипотеза (все слова пропущены)."""
    word_units, char_units, ids = [], [], []
    critical_total = critical_kept = 0
    silence_total = silence_false = silence_inserted = 0
    failed = punctuated = capitalised = ok_count = 0
    by_slice: Dict[str, List[int]] = defaultdict(list)
    rtfs, seconds = [], []
    for row in gold_rows:
        run = runs.get(row["id"])
        if run is None:
            continue
        ok = run.get("status") == "ok"
        hypothesis = run.get("hypothesis", "") if ok else ""
        failed += not ok
        reference = row.get("reference_text", "")
        if not normalize(reference):
            silence_total += 1
            inserted = len(normalize(hypothesis).split())
            silence_false += inserted > 0
            silence_inserted += inserted
            continue
        word_units.append([*word_errors(reference, hypothesis).values()])
        char_units.append([*char_errors(reference, hypothesis).values()])
        ids.append(row["id"])
        for tag in (f"condition:{row.get('condition', 'unknown')}", f"origin:{row.get('origin', 'unknown')}"):
            by_slice[tag].append(len(word_units) - 1)
        for item in row.get("critical", []):
            variants = [item] if isinstance(item, str) else item
            critical_total += 1
            critical_kept += any(phrase_preserved(variant, hypothesis) for variant in variants)
        if ok:
            ok_count += 1
            stripped = hypothesis.strip()
            punctuated += bool(re.search(r"[.,!?]", stripped))
            capitalised += bool(stripped[:1].isupper())
            if run.get("audio_seconds") and run.get("seconds") is not None and not run.get("warmup"):
                rtfs.append(run["seconds"] / run["audio_seconds"])
                seconds.append(run["seconds"])
    if not word_units:
        return {"n": 0}
    words = np.array(word_units, dtype=float)
    chars = np.array(char_units, dtype=float)
    low, high = bootstrap_ci(words, _corpus_rate)
    slices = {tag: _corpus_rate(words[indexes]) for tag, indexes in sorted(by_slice.items())}
    return {
        "n": len(ids), "failed": failed,
        "wer": _corpus_rate(words), "wer_ci95": [low, high], "cer": _corpus_rate(chars),
        "wer_by_slice": slices,
        "critical": {"total": critical_total, "preserved": critical_kept,
                     "rate": critical_kept / critical_total if critical_total else None},
        "silence": {"n": silence_total, "false_text": silence_false, "inserted_words": silence_inserted},
        "punctuation_rate": punctuated / ok_count if ok_count else None,
        "capitalised_rate": capitalised / ok_count if ok_count else None,
        "rtf": latency_summary(rtfs), "seconds": latency_summary(seconds),
    }
