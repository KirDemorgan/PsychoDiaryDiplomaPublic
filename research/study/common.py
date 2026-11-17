from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any, Dict, Iterable, Iterator, List, Optional, Set, Tuple

import yaml

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
RESULTS = ROOT / "results"
BACKEND_SCHEMAS = ROOT.parent / "backend" / "src" / "main" / "resources" / "ai" / "schemas"
BACKEND_WORKER = ROOT.parent / "backend" / "src" / "main" / "java" / "alex" / "drankov" / "diary" / "ai" / "service" / "AiWorker.java"


def read_jsonl(path: Path) -> List[Dict[str, Any]]:
    path = Path(path)
    if not path.exists():
        return []
    with path.open(encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def write_jsonl(path: Path, rows: Iterable[Dict[str, Any]]) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    temporary.replace(path)


def append_jsonl(path: Path, row: Dict[str, Any]) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(row, ensure_ascii=False) + "\n")
        handle.flush()


def write_json(path: Path, value: Any) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_json(path: Path) -> Any:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def stable_hash(value: Any) -> str:
    return sha256_text(json.dumps(value, ensure_ascii=False, sort_keys=True))


def utf16_length(text: str) -> int:
    return len(text.encode("utf-16-le")) // 2


def find_quote(text: str, quote: str, occurrence: int = 0) -> Optional[Tuple[int, int]]:
    """Возвращает позиции цитаты в UTF-16 (start включительно, end исключительно), как в backend."""
    if not quote or occurrence < 0:
        return None
    start = -1
    for _ in range(occurrence + 1):
        start = text.find(quote, start + 1)
        if start < 0:
            return None
    return utf16_length(text[:start]), utf16_length(text[: start + len(quote)])


def load_config(path: Optional[Path] = None) -> Dict[str, Any]:
    path = Path(path) if path else ROOT / "configs" / "study.yaml"
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def split_for_group(group_id: str, dev_share: float = 0.25) -> str:
    """Детерминированное разделение: группа целиком попадает в dev или test."""
    bucket = int(sha256_text(group_id)[:8], 16) / 0xFFFFFFFF
    return "dev" if bucket < dev_share else "test"


class RunStore:
    """Результаты одного прогона: JSONL, дописывается после каждого примера, прогон можно продолжить."""

    def __init__(self, path: Path, retry_errors: bool = False):
        self.path = Path(path)
        self.retry_errors = retry_errors
        self.rows = read_jsonl(self.path)
        self._latest: Dict[str, Dict[str, Any]] = {}
        for row in self.rows:
            self._latest[row["id"]] = row

    def is_done(self, sample_id: str, key: str) -> bool:
        row = self._latest.get(sample_id)
        if row is None or row.get("key") != key:
            return False
        return not (self.retry_errors and row.get("status") != "ok")

    def put(self, row: Dict[str, Any]) -> None:
        append_jsonl(self.path, row)
        self._latest[row["id"]] = row

    def latest(self) -> List[Dict[str, Any]]:
        return list(self._latest.values())


def backend_prompt(name: str) -> str:
    """Системный промпт берётся из AiWorker.java, чтобы эксперимент и сервер не расходились."""
    import re
    source = BACKEND_WORKER.read_text(encoding="utf-8")
    match = re.search(r"static final String " + name + r" = (.*?)\";\n", source, re.DOTALL)
    if match is None:
        raise ValueError(f"В AiWorker.java нет константы {name}")
    literals = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1) + '"')
    return "".join(literal.replace('\\"', '"').replace("\\\\", "\\") for literal in literals)
