from __future__ import annotations

import platform
import sys
from datetime import datetime, timezone
from importlib import metadata
from typing import Any, Dict, Iterable


def describe(packages: Iterable[str] = ()) -> Dict[str, Any]:
    """Условия прогона для воспроизводимости: система, Python, версии библиотек, GPU."""
    versions = {}
    for name in packages:
        try:
            versions[name] = metadata.version(name)
        except metadata.PackageNotFoundError:
            versions[name] = None
    result: Dict[str, Any] = {"started_at": datetime.now(timezone.utc).isoformat(), "python": sys.version.split()[0],
                              "platform": platform.platform(), "packages": versions}
    try:
        import torch
        result["torch"] = torch.__version__
        if torch.cuda.is_available():
            properties = torch.cuda.get_device_properties(0)
            result["gpu"] = {"name": properties.name, "vram_gb": round(properties.total_memory / 2**30, 2)}
    except ImportError:
        pass
    return result


def peak_vram_gb() -> float:
    try:
        import torch
        if torch.cuda.is_available():
            return round(torch.cuda.max_memory_allocated() / 2**30, 3)
    except ImportError:
        pass
    return 0.0
