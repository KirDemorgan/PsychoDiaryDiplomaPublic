from __future__ import annotations

import io
from pathlib import Path

import numpy as np
import soundfile
from scipy.signal import resample_poly

TARGET_RATE = 16000


def to_wav(data: bytes, target: Path) -> float:
    samples, rate = soundfile.read(io.BytesIO(data), dtype="float32", always_2d=True)
    mono = samples.mean(axis=1)
    if rate != TARGET_RATE:
        divisor = int(np.gcd(rate, TARGET_RATE))
        mono = resample_poly(mono, TARGET_RATE // divisor, rate // divisor)
    target.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(str(target), mono, TARGET_RATE, subtype="PCM_16")
    return len(mono) / TARGET_RATE
