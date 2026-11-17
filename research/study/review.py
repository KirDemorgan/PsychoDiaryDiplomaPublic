from __future__ import annotations

import argparse
import csv
import random

from .common import DATA, RESULTS, read_json, read_jsonl, write_json
from .facts import alias, sentence
from .evaluate import load_runs

FIELDS = ["blind_id", "period_id", "text", "clarity_1_5", "unsupported_0_1", "missed_important_0_2", "comment"]


def quotation(fact) -> str:
    """Цитата с датой записи: дата входит в факт, который видит модель, поэтому оценщику она тоже нужна."""
    value = fact["value"]
    return f"{value['date']}: «{value['quote']}»" if value.get("date") else f"«{value['quote']}»"


def write_facts(split: str, rows) -> None:
    """Факты периода рядом с каждым слепым текстом: по ним оценщик судит о «неподдержанном» и «пропуске»."""
    periods = {row["id"]: row for row in read_jsonl(DATA / "periods.jsonl")}
    lines = [f"# Факты к слепой оценке ({split})", "",
             "Для каждого обзора ниже факты его периода. ★ — важные факты (по ним отвечаем на «пропуск важного»). "
             "Утверждение «неподдержанное» = смысл, которого нет в этих фактах.", ""]
    for row in sorted(rows, key=lambda item: item["blind_id"]):
        period = periods[row["period_id"]]
        required = set(period["required_fact_ids"])
        lines += [f"## {row['blind_id']} ({period['id']}, дней {period['days']})", ""]
        for index, fact in enumerate(period["facts"]):
            text = quotation(fact) if fact["type"] == "QUOTE" else sentence(fact)
            lines.append(f"- {'★ ' if fact['factId'] in required else ''}{alias(index)}: {text}")
        lines.append("")
    (RESULTS / "review" / f"report_blind_{split}.facts.md").write_text("\n".join(lines), encoding="utf-8")


def export(split: str, systems=None, skip_fallback: bool = False) -> None:
    """Слепая выгрузка обзоров для ручной оценки: имя системы скрыто, порядок перемешан.

    systems — только эти системы; skip_fallback — не выгружать тексты, заменённые шаблоном (дубликат системы template)."""
    runs = load_runs("report", split)
    rows, key = [], {}
    for system, items in runs.items():
        if systems and system not in systems:
            continue
        for period_id, run in items.items():
            if skip_fallback and system != "template" and run.get("fallback"):
                continue
            rows.append({"system": system, "period_id": period_id, "text": " ".join(c["text"] for c in run["published"])})
    random.Random(20261016).shuffle(rows)
    for index, row in enumerate(rows, 1):
        blind = f"r{index:03}"
        key[blind] = {"system": row.pop("system"), "period_id": row["period_id"]}
        row["blind_id"] = blind
    target = RESULTS / "review"
    target.mkdir(parents=True, exist_ok=True)
    with (target / f"report_blind_{split}.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, FIELDS)
        writer.writeheader()
        writer.writerows({**row, "clarity_1_5": "", "unsupported_0_1": "", "missed_important_0_2": "", "comment": ""} for row in rows)
    write_json(target / f"report_blind_{split}.key.json", key)
    write_facts(split, rows)
    print(f"{target}: выгружено {len(rows)} обзоров. Заполните CSV, ключ не открывайте.")


def kappa(first, second, categories, weighted: bool = False):
    """Каппа Коэна; weighted — квадратичные веса для порядковых шкал. None, если ожидаемое согласие равно единице."""
    size = len(categories)
    index = {value: position for position, value in enumerate(categories)}
    total = len(first)
    observed = [[0.0] * size for _ in range(size)]
    for a, b in zip(first, second):
        observed[index[a]][index[b]] += 1 / total
    row = [sum(line) for line in observed]
    column = [sum(observed[i][j] for i in range(size)) for j in range(size)]
    weight = lambda i, j: ((i - j) / (size - 1)) ** 2 if weighted and size > 1 else float(i != j)
    disagree = sum(weight(i, j) * observed[i][j] for i in range(size) for j in range(size))
    chance = sum(weight(i, j) * row[i] * column[j] for i in range(size) for j in range(size))
    return None if chance == 0 else 1 - disagree / chance


def read_scores(path):
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return {row["blind_id"]: row for row in csv.DictReader(handle) if row.get("clarity_1_5")}


def compare(split: str) -> None:
    """Согласие человека (report_blind_<split>.human.csv) и модели-судьи (report_blind_<split>.csv) по общим текстам."""
    target = RESULTS / "review"
    human = read_scores(target / f"report_blind_{split}.human.csv")
    judge = read_scores(target / f"report_blind_{split}.csv")
    shared = sorted(set(human) & set(judge))
    if not shared:
        raise SystemExit("Нет общих оценённых текстов: заполните report_blind_<split>.human.csv (команда blank).")
    fields = {"clarity": (lambda r: int(float(r["clarity_1_5"])), list(range(1, 6)), True),
              "unsupported": (lambda r: int(r["unsupported_0_1"] or 0), [0, 1], False),
              "missed": (missed_level, [0, 1, 2], True)}
    result = {"n": len(shared)}
    for name, (value, categories, ordinal) in fields.items():
        a = [value(human[b]) for b in shared]
        c = [value(judge[b]) for b in shared]
        entry = {"exact_agreement": sum(x == y for x, y in zip(a, c)) / len(shared),
                 "kappa": kappa(a, c, categories, weighted=ordinal), "mean_human": sum(a) / len(a), "mean_judge": sum(c) / len(c)}
        if name == "clarity":
            entry["within_one"] = sum(abs(x - y) <= 1 for x, y in zip(a, c)) / len(shared)
        result[name] = entry
    write_json(RESULTS / "metrics" / f"report_review_agreement__{split}.json", result)
    print(result)
    if len(shared) < 20:
        print("Предупреждение: меньше 20 общих текстов, каппа ненадёжна.")


def blank(split: str, sample: int = 0) -> None:
    """Пустой лист для человека: те же слепые номера и тексты, без оценок судьи."""
    target = RESULTS / "review"
    with (target / f"report_blind_{split}.csv").open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if sample and sample < len(rows):
        rows = sorted(random.Random(20261024).sample(rows, sample), key=lambda row: row["blind_id"])
    path = target / f"report_blind_{split}.human.csv"
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, FIELDS)
        writer.writeheader()
        writer.writerows({"blind_id": r["blind_id"], "period_id": r["period_id"], "text": r["text"], "clarity_1_5": "",
                          "unsupported_0_1": "", "missed_important_0_2": "", "comment": ""} for r in rows)
    print(f"{path}: {len(rows)} текстов. Ключ report_blind_{split}.key.json не открывайте.")


def missed_level(row) -> int:
    """0 — все группы ★ упомянуты, 1 — значения сжаты до диапазона или обобщены, 2 — группа ★ не упомянута.

    Старый файл с бинарной колонкой missed_important_0_1: единица означала пропуск группы, то есть уровень 2."""
    if row.get("missed_important_0_2") not in (None, ""):
        return int(row["missed_important_0_2"])
    return 2 * int(row.get("missed_important_0_1") or 0)


def import_(split: str) -> None:
    target = RESULTS / "review"
    key = read_json(target / f"report_blind_{split}.key.json")
    sums = {}
    with (target / f"report_blind_{split}.csv").open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            if not row["clarity_1_5"]:
                continue
            system = key[row["blind_id"]]["system"]
            entry = sums.setdefault(system, {"n": 0, "clarity": 0.0, "unsupported": 0, "partial": 0, "missed": 0})
            entry["n"] += 1
            entry["clarity"] += float(row["clarity_1_5"])
            entry["unsupported"] += int(row["unsupported_0_1"] or 0)
            entry["partial"] += missed_level(row) == 1
            entry["missed"] += missed_level(row) == 2
    result = {system: {"n": v["n"], "clarity_mean": v["clarity"] / v["n"], "unsupported_rate": v["unsupported"] / v["n"],
                       "partial_omission_rate": v["partial"] / v["n"], "missed_important_rate": v["missed"] / v["n"]}
              for system, v in sums.items()}
    write_json(RESULTS / "metrics" / f"report_review__{split}.json", result)
    print(result)


def main() -> None:
    parser = argparse.ArgumentParser(description="Ручная слепая оценка обзоров")
    parser.add_argument("command", choices=["export", "import", "blank", "compare"])
    parser.add_argument("--split", choices=["dev", "test"], default="dev")
    parser.add_argument("--systems", default="", help="export: системы через запятую (например template,gemma4-e4b-freenq)")
    parser.add_argument("--sample", type=int, default=0, help="blank: случайная подвыборка из N текстов")
    parser.add_argument("--skip-fallback", action="store_true", help="export: не выгружать откаты на шаблон")
    args = parser.parse_args()
    if args.command == "export":
        export(args.split, [s.strip() for s in args.systems.split(",") if s.strip()] or None, args.skip_fallback)
    elif args.command == "import":
        import_(args.split)
    elif args.command == "blank":
        blank(args.split, args.sample)
    else:
        compare(args.split)


if __name__ == "__main__":
    main()
