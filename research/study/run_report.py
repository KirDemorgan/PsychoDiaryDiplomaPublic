from __future__ import annotations

import argparse
import time

from . import environment, llm, overview
from .common import DATA, RESULTS, RunStore, load_config, read_jsonl, stable_hash, write_json
from .run_nlp import ollama_digest


PROMPTS = {"free": overview.PROMPT_FREE, "freefb": overview.PROMPT_FREE, "freenq": overview.PROMPT_FREE,
           "freefbnq": overview.PROMPT_FREE, "slots": overview.PROMPT_SLOTS,
           "v1": overview.PROMPT_V1}


def select_rows(split: str, allow_draft: bool, limit: int):
    rows = [row for row in read_jsonl(DATA / "periods.jsonl") if row["split"] == split]
    if split == "test" and not allow_draft:
        approved = [row for row in rows if row.get("review_status") == "approved"]
        if len(approved) != len(rows):
            raise SystemExit(f"test: утверждено {len(approved)} из {len(rows)} периодов. "
                             "Просмотрите пакеты фактов и утвердите их или передайте --allow-draft (результат не независим).")
    return rows[:limit] if limit else rows


def main() -> None:
    parser = argparse.ArgumentParser(description="Обзор для PDF: шаблон (--arm template) или LLM (--arm free|freefb|freenq|freefbnq|slots|v1)")
    parser.add_argument("--arm", choices=["template", *overview.ARMS], required=True)
    parser.add_argument("--model", default="", help="id модели из configs/study.yaml (для v1 и free)")
    parser.add_argument("--split", choices=["dev", "test"], default="dev")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--retry-errors", action="store_true")
    parser.add_argument("--allow-draft", action="store_true")
    args = parser.parse_args()
    if args.arm != "template" and not args.model:
        parser.error("для режимов LLM нужен --model")

    name = "template" if args.arm == "template" else f"{args.model}-{args.arm}"
    config = load_config()
    rows = select_rows(args.split, args.allow_draft, args.limit)
    out = RESULTS / "report" / f"{name}__{args.split}.jsonl"
    store = RunStore(out, retry_errors=args.retry_errors)
    meta = environment.describe(["requests", "jsonschema"])
    meta.update({"split": args.split, "arm": args.arm, "rows": len(rows), "allow_draft": args.allow_draft,
                 "prompt_sha256": stable_hash(PROMPTS.get(args.arm, ""))})
    client = None
    if args.arm != "template":
        model_config = next(item for item in config["llm_models"] if item["id"] == args.model)
        settings = config["ollama"]
        client = llm.OllamaClient(settings["endpoint"], model_config["tag"], settings["num_ctx"],
                                  settings["num_predict"], settings["timeout_s"])
        meta.update({"model": model_config, "ollama": settings, **ollama_digest(settings["endpoint"], model_config["tag"])})
        client.chat("Ответь одним словом.", "Привет", {"type": "object", "properties": {"ok": {"type": "string"}}})
    key_base = [meta.get("model"), meta["prompt_sha256"], meta.get("ollama"), args.arm]

    pending = [r for r in rows if not store.is_done(r["id"], stable_hash([key_base, r["facts"]]))]
    print(f"{name}/{args.split}: всего {len(rows)}, осталось {len(pending)}")
    for number, row in enumerate(pending, 1):
        key = stable_hash([key_base, row["facts"]])
        started = time.perf_counter()
        fallback = overview.template_overview(row["facts"])
        try:
            if client is None:
                result = {"status": "ok", "claims": fallback, "valid_first": True, "valid_final": True, "calls": [],
                          "seconds": time.perf_counter() - started}
            else:
                result = {"status": "ok", **overview.generate(client, args.arm, row["facts"])}
        except Exception as error:  # сбой транспорта: публикуется шаблон, причина сохраняется
            result = {"status": "error", "error": f"{type(error).__name__}: {error}"[:300], "claims": None,
                      "valid_first": False, "valid_final": False, "calls": [], "seconds": time.perf_counter() - started}
        published = result["claims"] if result.get("claims") else fallback
        store.put({"id": row["id"], "key": key, "model": name, "arm": args.arm, "fallback": not result["valid_final"],
                   "published": published, **result})
        if number % 5 == 0 or number == len(pending):
            print(f"  {number}/{len(pending)}")
    write_json(out.with_suffix(".meta.json"), meta)


if __name__ == "__main__":
    main()
