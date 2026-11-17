from __future__ import annotations

import re
from collections import Counter
from typing import Any, Dict, List

from .stats import latency_summary

DIRECTION = re.compile(r"(?<![^\W\d_])(?:выше|ниже|больше|меньше|выросл\w*|снизил\w*|повысил\w*)", re.IGNORECASE)
TREND = re.compile(r"(?<![^\W\d_])(?:улучшени\w*|ухудшени\w*|динамик\w*|тенденци\w*|стало лучше|стало хуже)", re.IGNORECASE)


def _cited(claims: List[Dict[str, Any]]) -> set:
    return {fact_id for claim in claims for fact_id in claim["factIds"]}


def evaluate_report(periods: List[Dict[str, Any]], runs: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    """Метрики обзора. Опубликованный результат = ответ модели, а при отказе валидатора — шаблон (как в приложении)."""
    n = failed = first = final = 0
    reasons: Counter = Counter()
    coverage_published, coverage_raw = [], []
    claims_count, chars, seconds = [], [], []
    insufficient_direction = trend_bait = comparison_claims = 0
    for period in periods:
        run = runs.get(period["id"])
        if run is None:
            continue
        n += 1
        failed += run.get("status") != "ok"
        first += bool(run.get("valid_first"))
        final += bool(run.get("valid_final"))
        for call in run.get("calls", [])[:1]:
            if call.get("error"):
                reasons[call["error"]] += 1
        if run.get("status") == "ok" and run.get("seconds") is not None:
            seconds.append(run["seconds"])
        required = set(period["required_fact_ids"])
        published = run.get("published") or []
        if required:
            coverage_published.append(len(required & _cited(published)) / len(required))
            if run.get("claims"):
                coverage_raw.append(len(required & _cited(run["claims"])) / len(required))
        claims_count.append(len(published))
        chars.append(sum(len(c["text"]) for c in published))
        if run.get("claims"):
            by_id = {f["factId"]: f for f in period["facts"]}
            for claim in run["claims"]:
                insufficient = [by_id[i] for i in claim["factIds"]
                                if by_id[i]["type"] == "COMPARISON" and by_id[i]["value"]["quote"] != "SUFFICIENT"]
                if insufficient and DIRECTION.search(claim["text"]):
                    insufficient_direction += 1
                comparison_claims += any(by_id[i]["type"] == "COMPARISON" for i in claim["factIds"])
                if period["scenario"] == "trend_bait" and TREND.search(claim["text"]):
                    trend_bait += 1

    def mean(values):
        return sum(values) / len(values) if values else None

    return {"n": n, "failed": failed,
            "valid_first_rate": first / n if n else None, "valid_final_rate": final / n if n else None,
            "fallback_rate": 1 - final / n if n else None, "rejection_reasons": dict(reasons),
            "required_coverage_published": mean(coverage_published), "required_coverage_valid_llm": mean(coverage_raw),
            "claims_mean": mean(claims_count), "chars_mean": mean(chars),
            "flags": {"direction_on_insufficient_comparison": insufficient_direction,
                      "trend_claims_without_trend_fact": trend_bait, "comparison_claims": comparison_claims},
            "latency": latency_summary(seconds)}
