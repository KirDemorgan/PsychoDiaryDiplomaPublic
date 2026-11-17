from __future__ import annotations

import argparse
import time

import requests

from . import environment, llm, rules
from .common import DATA, RESULTS, RunStore, load_config, read_jsonl, stable_hash, write_json


def select_rows(split: str, origin: str, allow_draft: bool, limit: int):
    rows = [row for row in read_jsonl(DATA / "texts.jsonl") if row["split"] == split]
    if origin != "all":
        rows = [row for row in rows if row["origin"] == origin]
    if split == "test" and not allow_draft:
        approved = [row for row in rows if row.get("review_status") == "approved"]
        if len(approved) != len(rows):
            raise SystemExit(f"test: утверждено {len(approved)} из {len(rows)} эталонов. "
                             "Утвердите их в студии или передайте --allow-draft (результат не будет независимым).")
    return rows[:limit] if limit else rows


def ollama_digest(endpoint: str, tag: str):
    try:
        base = endpoint.split("/api/")[0]
        models = requests.get(base + "/api/tags", timeout=10).json().get("models", [])
        match = next((m for m in models if m.get("name") == tag or m.get("model") == tag), None)
        version = requests.get(base + "/api/version", timeout=10).json().get("version")
        return {"digest": match.get("digest") if match else None, "details": match.get("details") if match else None,
                "ollama_version": version}
    except (requests.RequestException, ValueError):
        return {}


def main() -> None:
    parser = argparse.ArgumentParser(description="Извлечение тегов: LLM через Ollama или словарь-baseline (--model rules)")
    parser.add_argument("--model", required=True)
    parser.add_argument("--split", choices=["dev", "test"], default="dev")
    parser.add_argument("--origin", default="all", help="all | template | challenge | hand")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--retry-errors", action="store_true")
    parser.add_argument("--allow-draft", action="store_true")
    args = parser.parse_args()

    config = load_config()
    rows = select_rows(args.split, args.origin, args.allow_draft, args.limit)
    out = RESULTS / "nlp" / f"{args.model}__{args.split}.jsonl"
    store = RunStore(out, retry_errors=args.retry_errors)
    meta = environment.describe(["requests", "jsonschema"])
    meta.update({"split": args.split, "origin": args.origin, "rows": len(rows), "allow_draft": args.allow_draft,
                 "prompt_version": llm.PROMPT_VERSION, "prompt_sha256": stable_hash(llm.PROMPT_ENTRY),
                 "schema_sha256": stable_hash(llm.entry_schema())})

    client = None
    if args.model != "rules":
        model_config = next(item for item in config["llm_models"] if item["id"] == args.model)
        settings = config["ollama"]
        client = llm.OllamaClient(settings["endpoint"], model_config["tag"], settings["num_ctx"],
                                  settings["num_predict"], settings["timeout_s"])
        meta.update({"model": model_config, "ollama": settings, **ollama_digest(settings["endpoint"], model_config["tag"])})
        client.chat("Ответь одним словом.", "Привет", {"type": "object", "properties": {"ok": {"type": "string"}}})
    key_base = [meta.get("model"), meta["prompt_sha256"], meta["schema_sha256"], meta.get("ollama")]

    pending = [row for row in rows if not store.is_done(row["id"], stable_hash([key_base, row["text"]]))]
    print(f"{args.model}/{args.split}: всего {len(rows)}, осталось {len(pending)}")
    for number, row in enumerate(pending, 1):
        key = stable_hash([key_base, row["text"]])
        started = time.perf_counter()
        try:
            if client is None:
                parsed = rules.extract(row["text"])
                result = {"status": "ok", "parsed": parsed, "valid_first": True, "valid_final": True,
                          "calls": [], "seconds": time.perf_counter() - started}
            else:
                outcome = llm.extract_with_retry(client, row["text"])
                result = {"status": "ok", **outcome}
        except Exception as error:  # сбой транспорта не превращается в «пустой удачный ответ»
            result = {"status": "error", "error": f"{type(error).__name__}: {error}"[:300], "parsed": None,
                      "valid_first": False, "valid_final": False, "seconds": time.perf_counter() - started}
        store.put({"id": row["id"], "key": key, "model": args.model, **result})
        if number % 10 == 0 or number == len(pending):
            print(f"  {number}/{len(pending)}")
    write_json(out.with_suffix(".meta.json"), meta)


if __name__ == "__main__":
    main()
