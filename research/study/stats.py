from __future__ import annotations

from typing import Callable, Dict, List, Optional, Sequence, Tuple

import numpy as np

SEED = 20261016
BOOTSTRAP_ITERATIONS = 2000


def percentile(values: Sequence[float], q: float) -> Optional[float]:
    values = [v for v in values if v is not None]
    return float(np.percentile(values, q)) if values else None


def latency_summary(values: Sequence[float]) -> Dict[str, Optional[float]]:
    return {"n": len([v for v in values if v is not None]), "median": percentile(values, 50), "p95": percentile(values, 95)}


def bootstrap_ci(units: np.ndarray, metric: Callable[[np.ndarray], float],
                 iterations: int = BOOTSTRAP_ITERATIONS, seed: int = SEED) -> Tuple[Optional[float], Optional[float]]:
    """Бутстрап по независимым единицам (строка массива = один текст или одна запись). Интервал 95% процентильный."""
    count = units.shape[0]
    if count < 2:
        return None, None
    rng = np.random.default_rng(seed)
    values = []
    for _ in range(iterations):
        sample = units[rng.integers(0, count, count)]
        value = metric(sample)
        if value is not None and not np.isnan(value):
            values.append(value)
    if not values:
        return None, None
    return float(np.percentile(values, 2.5)), float(np.percentile(values, 97.5))
