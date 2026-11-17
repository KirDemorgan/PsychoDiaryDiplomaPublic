from __future__ import annotations

import argparse
from typing import Any, Dict, List

from .common import DATA, RESULTS, read_jsonl, write_json
from .metrics_asr import evaluate_asr
from .metrics_nlp import evaluate_nlp
from .metrics_report import evaluate_report


def load_runs(task: str, split: str) -> Dict[str, Dict[str, Dict[str, Any]]]:
    runs = {}
    for path in sorted((RESULTS / task).glob(f"*__{split}.jsonl")):
        latest = {}
        for row in read_jsonl(path):
            latest[row["id"]] = row
        runs[path.name.split("__")[0]] = latest
    return runs


def gold_rows(task: str, split: str, allow_draft: bool) -> List[Dict[str, Any]]:
    source = {"nlp": "texts.jsonl", "asr": "audio.jsonl", "report": "periods.jsonl"}[task]
    rows = [row for row in read_jsonl(DATA / source) if row["split"] == split]
    if split == "test" and not allow_draft:
        rows = [row for row in rows if row.get("review_status") == "approved"]
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description="Подсчёт метрик по сохранённым ответам (модели не вызываются)")
    parser.add_argument("task", choices=["asr", "nlp", "report"])
    parser.add_argument("--split", choices=["dev", "test"], default="dev")
    parser.add_argument("--allow-draft", action="store_true")
    args = parser.parse_args()

    rows = gold_rows(args.task, args.split, args.allow_draft)
    output: Dict[str, Any] = {"task": args.task, "split": args.split, "gold_rows": len(rows),
                              "gold_approved": sum(r.get("review_status") == "approved" for r in rows),
                              "models": {}}
    for model, runs in load_runs(args.task, args.split).items():
        if args.task == "asr":
            output["models"][model] = {"all": evaluate_asr(rows, runs)}
        elif args.task == "report":
            output["models"][model] = {"all": evaluate_report(rows, runs)}
        else:
            slices = {"all": rows}
            for origin in sorted({r["origin"] for r in rows}):
                slices[origin] = [r for r in rows if r["origin"] == origin]
            output["models"][model] = {name: evaluate_nlp(subset, runs) for name, subset in slices.items()}
    target = RESULTS / "metrics" / f"{args.task}__{args.split}.json"
    write_json(target, output)
    print(f"{target}: моделей {len(output['models'])}")


if __name__ == "__main__":
    main()
