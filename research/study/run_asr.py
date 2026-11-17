from __future__ import annotations

import argparse
import time
from pathlib import Path

from . import asr_models, environment
from .common import DATA, RESULTS, RunStore, load_config, read_jsonl, sha256_file, stable_hash, write_json


def select_rows(split: str, allow_draft: bool, limit: int):
    rows = [row for row in read_jsonl(DATA / "audio.jsonl") if row["split"] == split]
    if split == "test" and not allow_draft:
        approved = [row for row in rows if row.get("review_status") == "approved"]
        if len(approved) != len(rows):
            raise SystemExit(f"test: утверждено {len(approved)} из {len(rows)} эталонов. "
                             "Утвердите их в студии или передайте --allow-draft (результат не будет независимым).")
    return rows[:limit] if limit else rows


def main() -> None:
    parser = argparse.ArgumentParser(description="Прогон ASR-модели по аудио исследования")
    parser.add_argument("--model", required=True)
    parser.add_argument("--split", choices=["dev", "test"], default="dev")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--retry-errors", action="store_true")
    parser.add_argument("--allow-draft", action="store_true")
    args = parser.parse_args()

    config = load_config()
    model_config = next(item for item in config["asr_models"] if item["id"] == args.model)
    rows = select_rows(args.split, args.allow_draft, args.limit)
    out = RESULTS / "asr" / f"{args.model}__{args.split}.jsonl"
    store = RunStore(out, retry_errors=args.retry_errors)

    meta = environment.describe(["torch", "transformers", "gigaam", "tone", "soundfile"])
    meta.update({"model": model_config, "split": args.split, "rows": len(rows), "allow_draft": args.allow_draft})
    adapter = asr_models.create(model_config)
    meta["adapter"] = adapter.info()

    pending = [row for row in rows if not store.is_done(row["id"], stable_hash([model_config, sha256_file(DATA / row["audio_path"])]))]
    print(f"{args.model}/{args.split}: всего {len(rows)}, осталось {len(pending)}")
    if pending:
        adapter.transcribe(DATA / pending[0]["audio_path"])

    for number, row in enumerate(pending, 1):
        path = DATA / row["audio_path"]
        key = stable_hash([model_config, sha256_file(path)])
        started = time.perf_counter()
        try:
            hypothesis = adapter.transcribe(path)
            result = {"status": "ok", "hypothesis": hypothesis}
        except Exception as error:  # сбой модели фиксируется и учитывается в метриках, прогон продолжается
            result = {"status": "error", "error": f"{type(error).__name__}: {error}"[:300], "hypothesis": ""}
        store.put({"id": row["id"], "key": key, "model": args.model, "seconds": time.perf_counter() - started,
                   "audio_seconds": row["audio_seconds"], **result})
        if number % 10 == 0 or number == len(pending):
            print(f"  {number}/{len(pending)}")

    meta["peak_vram_gb"] = environment.peak_vram_gb()
    write_json(out.with_suffix(".meta.json"), meta)


if __name__ == "__main__":
    main()
