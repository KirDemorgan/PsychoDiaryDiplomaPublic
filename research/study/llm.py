from __future__ import annotations

import json
import time
from typing import Any, Dict, List, Optional, Tuple

import jsonschema
import requests

from .common import BACKEND_SCHEMAS, backend_prompt, find_quote, utf16_length

PROMPT_ENTRY = backend_prompt("ENTRY_PROMPT")
PROMPT_VERSION = "entry-extraction-v2"
SCHEMA_VERSION = "entry-candidates-v1"
MAX_OUTPUT_UNITS = 65536


def entry_schema() -> Dict[str, Any]:
    import json as _json
    return _json.loads((BACKEND_SCHEMAS / "entry-candidates-v1.json").read_text(encoding="utf-8"))


def user_message(text: str) -> str:
    return json.dumps({"text": text, "occurredAt": "2026-10-01T18:00:00+05:00",
                       "timeZone": "Asia/Yekaterinburg", "revision": 1}, ensure_ascii=False)


class OllamaClient:
    """Тот же контракт, что у OllamaModelAdapter в backend: /api/chat, JSON Schema, think=false, temperature=0."""

    def __init__(self, endpoint: str, model: str, num_ctx: int = 8192, num_predict: int = 2048,
                 timeout_s: float = 120, token: str = ""):
        self.endpoint = endpoint
        self.model = model
        self.num_ctx = num_ctx
        self.num_predict = num_predict
        self.timeout_s = timeout_s
        self.headers = {"Authorization": f"Bearer {token}"} if token else {}

    def chat(self, system: str, user: str, schema: Dict[str, Any],
             history: Optional[List[Dict[str, str]]] = None) -> Dict[str, Any]:
        payload = {
            "model": self.model,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}, *(history or [])],
            "format": schema, "stream": False, "think": False,
            "options": {"temperature": 0, "num_ctx": self.num_ctx, "num_predict": self.num_predict},
        }
        started = time.perf_counter()
        response = requests.post(self.endpoint, json=payload, headers=self.headers, timeout=self.timeout_s)
        elapsed = time.perf_counter() - started
        response.raise_for_status()
        body = response.json()
        if body.get("done") is not True or body.get("done_reason") != "stop":
            raise RuntimeError(f"Генерация не завершена: done_reason={body.get('done_reason')}")
        content = body.get("message", {}).get("content")
        if not isinstance(content, str):
            raise RuntimeError("В ответе нет текста")
        return {"content": content, "seconds": elapsed,
                "prompt_tokens": body.get("prompt_eval_count"), "output_tokens": body.get("eval_count"),
                "load_seconds": (body.get("load_duration") or 0) / 1e9}


def strict_json(raw: str) -> Any:
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("Повторяющийся ключ JSON")
            result[key] = value
        return result
    if utf16_length(raw) > MAX_OUTPUT_UNITS:
        raise ValueError("Ответ длиннее лимита")
    return json.loads(raw, object_pairs_hook=pairs)


def validate_extraction(raw: str, text: str, schema: Optional[Dict[str, Any]] = None) -> Tuple[Optional[Dict[str, Any]], Optional[str]]:
    """Проверка ответа так же, как на сервере: схема, существование цитаты, отсутствие дублей."""
    try:
        parsed = strict_json(raw)
        jsonschema.validate(parsed, schema or entry_schema())
        seen = set()
        for candidate in parsed["candidates"]:
            quote = candidate["quote"]
            occurrence = candidate["occurrence"] if text.count(quote) > 1 else 0
            span = find_quote(text, quote, occurrence)
            if span is None:
                return None, "QUOTE_NOT_FOUND"
            key = (candidate["labelCode"], span)
            if key in seen:
                return None, "DUPLICATE_CANDIDATE"
            seen.add(key)
        return parsed, None
    except (ValueError, jsonschema.ValidationError) as error:
        return None, type(error).__name__


def extract_with_retry(client: OllamaClient, text: str, attempts: int = 2) -> Dict[str, Any]:
    """Не более двух вызовов, как в backend. Ошибки транспорта не маскируются под невалидный JSON."""
    schema = entry_schema()
    user = user_message(text)
    calls = []
    for _ in range(attempts):
        reply = client.chat(PROMPT_ENTRY, user, schema)
        parsed, error = validate_extraction(reply["content"], text, schema)
        calls.append({**reply, "error": error})
        if parsed is not None:
            break
    return {"parsed": parsed, "calls": calls, "valid_first": calls[0]["error"] is None,
            "valid_final": parsed is not None, "seconds": sum(call["seconds"] for call in calls)}
