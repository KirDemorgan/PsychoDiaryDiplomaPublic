from __future__ import annotations

from collections import Counter, defaultdict
from typing import Any, Dict, List, Optional

import numpy as np

from .labels import LABELS, actual_labels
from .stats import bootstrap_ci, latency_summary


def _f1(tp: float, fp: float, fn: float) -> Dict[str, Optional[float]]:
    precision = tp / (tp + fp) if tp + fp else None
    recall = tp / (tp + fn) if tp + fn else None
    f1 = 2 * tp / (2 * tp + fp + fn) if 2 * tp + fp + fn else None
    return {"precision": precision, "recall": recall, "f1": f1}


def _macro(units: np.ndarray, label_indexes: List[int]) -> float:
    totals = units.sum(axis=0)
    scores = []
    for index in label_indexes:
        tp, fp, fn = totals[index]
        if 2 * tp + fp + fn:
            scores.append(2 * tp / (2 * tp + fp + fn))
    return float(np.mean(scores)) if scores else float("nan")


def _micro(units: np.ndarray) -> float:
    tp, fp, fn = units.sum(axis=(0, 1))
    return float(2 * tp / (2 * tp + fp + fn)) if 2 * tp + fp + fn else float("nan")


def _event_key(event: Dict[str, Any]):
    return (event["labelCode"], event["presence"], event["temporal"], event["subject"])


def evaluate_nlp(gold_rows: List[Dict[str, Any]], runs: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    """Метрики извлечения. Невалидный или отсутствующий ответ считается пустым предсказанием (все признаки пропущены)."""
    label_index = {label: i for i, label in enumerate(LABELS)}
    units = np.zeros((len(gold_rows), len(LABELS), 3))
    event_tp = event_fp = event_fn = 0
    false_actual = defaultdict(lambda: [0, 0])
    first_valid = final_valid = failed = 0
    latencies = []
    evaluated = 0
    for row_number, row in enumerate(gold_rows):
        run = runs.get(row["id"])
        if run is None:
            continue
        evaluated += 1
        parsed = run.get("parsed")
        predicted = parsed["candidates"] if parsed else []
        if run.get("status", "ok") != "ok":
            failed += 1
        first_valid += bool(run.get("valid_first"))
        final_valid += bool(run.get("valid_final"))
        if run.get("seconds") is not None and run.get("status", "ok") == "ok":
            latencies.append(run["seconds"])
        gold_actual, predicted_actual = actual_labels(row["events"]), actual_labels(predicted)
        for label in LABELS:
            i = label_index[label]
            units[row_number, i, 0] = label in gold_actual and label in predicted_actual
            units[row_number, i, 1] = label not in gold_actual and label in predicted_actual
            units[row_number, i, 2] = label in gold_actual and label not in predicted_actual
        counter_gold = Counter(_event_key(e) for e in row["events"])
        counter_pred = Counter(_event_key(e) for e in predicted)
        matched = sum((counter_gold & counter_pred).values())
        event_tp += matched
        event_fp += sum(counter_pred.values()) - matched
        event_fn += sum(counter_gold.values()) - matched
        scenario = row.get("scenario", "unknown")
        false_actual[scenario][1] += 1
        if predicted_actual - gold_actual:
            false_actual[scenario][0] += 1
    used = units[[i for i, row in enumerate(gold_rows) if row["id"] in runs]] if evaluated else units[:0]
    totals = used.sum(axis=0) if evaluated else np.zeros((len(LABELS), 3))
    per_label = {}
    for label in LABELS:
        tp, fp, fn = totals[label_index[label]]
        per_label[label] = {"tp": int(tp), "fp": int(fp), "fn": int(fn), "support": int(tp + fn), **_f1(tp, fp, fn)}
    supported = [label_index[label] for label in LABELS if per_label[label]["support"] > 0]
    micro = _f1(*used.sum(axis=(0, 1))) if evaluated else {"precision": None, "recall": None, "f1": None}
    macro_value = _macro(used, supported) if evaluated else float("nan")
    micro_low, micro_high = bootstrap_ci(used, _micro)
    macro_low, macro_high = bootstrap_ci(used, lambda sample: _macro(sample, supported))
    return {
        "n": evaluated, "failed": failed,
        "valid_first_rate": first_valid / evaluated if evaluated else None,
        "valid_final_rate": final_valid / evaluated if evaluated else None,
        "micro": {**micro, "ci95": [micro_low, micro_high]},
        "macro_f1": None if np.isnan(macro_value) else macro_value,
        "macro_f1_ci95": [macro_low, macro_high],
        "macro_labels": len(supported),
        "per_label": per_label,
        "event_level": _f1(event_tp, event_fp, event_fn),
        "false_actual_by_scenario": {s: {"false": a, "n": n, "rate": a / n} for s, (a, n) in sorted(false_actual.items())},
        "latency": latency_summary(latencies),
    }
