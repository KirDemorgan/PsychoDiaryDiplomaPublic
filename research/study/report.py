from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any, Dict, List, Optional

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402

from .common import RESULTS, load_config, read_json  # noqa: E402
from .labels import LABELS, LABEL_RU  # noqa: E402

SURFACE, INK, INK_SECONDARY, GRID = "#fcfcfb", "#0b0b0b", "#52514e", "#e4e3df"
SERIES = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4"]
BASELINE_COLOR = "#8a8984"
FIGURES = RESULTS / "figures"


def colors_for(models: List[str], order: List[str]) -> Dict[str, str]:
    mapping = {}
    for name in order:
        mapping[name] = SERIES[len(mapping) % len(SERIES)]
    for name in models:
        mapping.setdefault(name, BASELINE_COLOR if name == "rules" else SERIES[len(mapping) % len(SERIES)])
    mapping["rules"] = BASELINE_COLOR
    return mapping


def style(ax, xlabel: str = "", ylabel: str = "") -> None:
    ax.set_facecolor(SURFACE)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(GRID)
    ax.tick_params(colors=INK_SECONDARY, labelsize=9)
    ax.set_xlabel(xlabel, color=INK_SECONDARY, fontsize=9)
    ax.set_ylabel(ylabel, color=INK_SECONDARY, fontsize=9)
    ax.grid(axis="x", color=GRID, linewidth=.6)
    ax.set_axisbelow(True)


def finish(fig, name: str, title: str, out: Path) -> str:
    fig.patch.set_facecolor(SURFACE)
    fig.suptitle(title, color=INK, fontsize=11, x=.02, ha="left", fontweight="bold")
    fig.tight_layout(rect=(0, 0, 1, .94))
    out.mkdir(parents=True, exist_ok=True)
    fig.savefig(out / f"{name}.png", dpi=200, facecolor=SURFACE)
    plt.close(fig)
    return f"figures/{name}.png"


def hbar(models: List[str], values: List[Optional[float]], errors: List[Optional[List[float]]], colors: Dict[str, str],
         title: str, xlabel: str, name: str, out: Path, fmt: str = "{:.3f}", limit: Optional[float] = None) -> str:
    fig, ax = plt.subplots(figsize=(7, 1.1 + .55 * len(models)))
    style(ax, xlabel)
    positions = np.arange(len(models))[::-1]
    for position, model, value, interval in zip(positions, models, values, errors):
        if value is None:
            ax.text(0, position, "нет данных", va="center", color=INK_SECONDARY, fontsize=9)
            continue
        ax.barh(position, value, height=.5, color=colors[model])
        if interval and None not in interval:
            ax.plot(interval, [position, position], color=INK, linewidth=1.2)
        ax.text(max(value, (interval or [0, 0])[1] or 0) + (limit or max(v for v in values if v is not None) or 1) * .02,
                position, fmt.format(value), va="center", color=INK, fontsize=9)
    ax.set_yticks(positions)
    ax.set_yticklabels(models, color=INK, fontsize=9)
    ax.set_xlim(0, limit or max([v for v in values if v is not None] + [1e-9]) * 1.25)
    return finish(fig, name, title, out)


def grouped(categories: List[str], models: List[str], matrix: Dict[str, List[Optional[float]]], colors: Dict[str, str],
            title: str, ylabel: str, name: str, out: Path, ymax: Optional[float] = None) -> str:
    fig, ax = plt.subplots(figsize=(max(6.5, 1.3 * len(categories) + 2), 3.6))
    style(ax, ylabel=ylabel)
    ax.grid(axis="y", color=GRID, linewidth=.6)
    ax.grid(axis="x", visible=False)
    width = .8 / max(len(models), 1)
    x = np.arange(len(categories))
    for index, model in enumerate(models):
        values = [v if v is not None else 0 for v in matrix[model]]
        bars = ax.bar(x + index * width - .4 + width / 2, values, width * .9, color=colors[model], label=model)
        for bar, raw in zip(bars, matrix[model]):
            if raw is not None:
                ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + (ymax or 1) * .01, f"{raw:.2f}",
                        ha="center", va="bottom", fontsize=7, color=INK)
    ax.set_xticks(x)
    ax.set_xticklabels(categories, color=INK, fontsize=9)
    if ymax:
        ax.set_ylim(0, ymax)
    ax.legend(frameon=False, fontsize=8, labelcolor=INK, loc="upper right")
    return finish(fig, name, title, out)


def heatmap(models: List[str], values: np.ndarray, title: str, name: str, out: Path) -> str:
    fig, ax = plt.subplots(figsize=(1.6 + 1.1 * len(models), 5))
    image = ax.imshow(values, cmap="Blues", vmin=0, vmax=1, aspect="auto")
    ax.set_xticks(range(len(models)))
    ax.set_xticklabels(models, rotation=0, color=INK, fontsize=8)
    ax.set_yticks(range(len(LABELS)))
    ax.set_yticklabels([LABEL_RU[label] for label in LABELS], color=INK, fontsize=8)
    for i in range(values.shape[0]):
        for j in range(values.shape[1]):
            if not np.isnan(values[i, j]):
                ax.text(j, i, f"{values[i, j]:.2f}", ha="center", va="center", fontsize=7,
                        color="white" if values[i, j] > .6 else INK)
    for spine in ax.spines.values():
        spine.set_visible(False)
    fig.colorbar(image, ax=ax, fraction=.04, pad=.02).ax.tick_params(labelsize=8, colors=INK_SECONDARY)
    return finish(fig, name, title, out)


def table(header: List[str], rows: List[List[str]]) -> str:
    lines = ["| " + " | ".join(header) + " |", "|" + "|".join("---" for _ in header) + "|"]
    lines += ["| " + " | ".join(row) + " |" for row in rows]
    return "\n".join(lines)


def num(value: Optional[float], digits: int = 3) -> str:
    return "—" if value is None else f"{value:.{digits}f}"


def interval(value) -> str:
    return "—" if not value or None in value else f"[{value[0]:.3f}; {value[1]:.3f}]"


def asr_section(data: Dict[str, Any], colors: Dict[str, str], out: Path) -> List[str]:
    models = list(data["models"])
    metrics = {model: data["models"][model]["all"] for model in models}
    lines = [f"## ASR ({data['split']})", "",
             f"Эталонных записей: {data['gold_rows']}, утверждено человеком: {data['gold_approved']}.", ""]
    lines.append(table(["Модель", "n", "Сбоев", "WER", "95% ДИ WER", "CER", "Критические слова", "Тишина: ложный текст",
                        "Знаки препинания", "RTF (медиана)", "RTF (p95)"],
                       [[m, str(x.get("n", 0)), str(x.get("failed", 0)), num(x.get("wer")), interval(x.get("wer_ci95")),
                         num(x.get("cer")), num((x.get("critical") or {}).get("rate")),
                         f"{(x.get('silence') or {}).get('false_text', 0)}/{(x.get('silence') or {}).get('n', 0)}",
                         num(x.get("punctuation_rate"), 2), num((x.get("rtf") or {}).get("median"), 3),
                         num((x.get("rtf") or {}).get("p95"), 3)] for m, x in metrics.items()]))
    figs = [hbar(models, [metrics[m].get("wer") for m in models], [metrics[m].get("wer_ci95") for m in models], colors,
                 "ASR: доля ошибочных слов (WER), меньше — лучше", "WER", f"asr_wer_{data['split']}", out),
            hbar(models, [(metrics[m].get("critical") or {}).get("rate") for m in models], [None] * len(models), colors,
                 "ASR: сохранённые критические слова (отрицания, числа), больше — лучше", "доля", f"asr_critical_{data['split']}",
                 out, limit=1.0)]
    slices = sorted({tag for m in models for tag in metrics[m].get("wer_by_slice", {})})
    if slices:
        figs.append(grouped([s.split(":", 1)[1] + f" ({s.split(':', 1)[0]})" for s in slices], models,
                            {m: [metrics[m].get("wer_by_slice", {}).get(s) for s in slices] for m in models}, colors,
                            "ASR: WER по условиям записи", "WER", f"asr_slices_{data['split']}", out))
    figs.append(hbar(models, [(metrics[m].get("rtf") or {}).get("median") for m in models], [None] * len(models), colors,
                     "ASR: скорость, RTF = время обработки / длительность записи (меньше — быстрее)", "RTF",
                     f"asr_rtf_{data['split']}", out))
    return lines + [""] + [f"![]({f})" for f in figs] + [""]


def nlp_section(data: Dict[str, Any], colors: Dict[str, str], out: Path) -> List[str]:
    models = list(data["models"])
    origins = [o for o in ("hand", "challenge", "template", "all") if all(o in data["models"][m] for m in models)]
    lines = [f"## Извлечение тегов ({data['split']})", "",
             f"Эталонных текстов: {data['gold_rows']}, утверждено человеком: {data['gold_approved']}.",
             "Основной срез — `hand` (тексты, написанные вручную). Срез `template` порождён шаблонами и завышает оценки.", ""]
    for origin in origins:
        rows = []
        for m in models:
            x = data["models"][m][origin]
            rows.append([m, str(x["n"]), num(x["macro_f1"]), interval(x["macro_f1_ci95"]), num(x["micro"]["f1"]),
                         num(x["micro"]["precision"]), num(x["micro"]["recall"]), num(x["event_level"]["f1"]),
                         num(x["valid_first_rate"], 2), num(x["valid_final_rate"], 2),
                         num(x["latency"]["median"], 2), num(x["latency"]["p95"], 2)])
        lines += [f"### Срез: {origin}", "", table(["Модель", "n", "macro-F1", "95% ДИ", "micro-F1", "P", "R", "event-F1",
                                                   "JSON с 1-й попытки", "JSON после повтора", "медиана, с", "p95, с"], rows), ""]
    figs = []
    main_origin = origins[0] if origins else "all"
    figs.append(grouped(origins, models, {m: [data["models"][m][o]["macro_f1"] for o in origins] for m in models}, colors,
                        "Извлечение тегов: macro-F1 по срезам данных, больше — лучше", "macro-F1",
                        f"nlp_f1_{data['split']}", out, ymax=1.1))
    values = np.array([[(data["models"][m]["all"]["per_label"][label]["f1"] if data["models"][m]["all"]["per_label"][label]["f1"] is not None else np.nan)
                        for m in models] for label in LABELS], dtype=float)
    figs.append(heatmap(models, values, "F1 по меткам (все срезы)", f"nlp_labels_{data['split']}", out))
    groups = ["negation", "past", "future", "other", "uncertain"]
    available = [g for g in groups if any(g in data["models"][m]["all"]["false_actual_by_scenario"] for m in models)]
    if available:
        figs.append(grouped(available, models, {m: [data["models"][m]["all"]["false_actual_by_scenario"].get(g, {}).get("rate")
                                                    for g in available] for m in models}, colors,
                            "Доля текстов, где модель выдала ложный «актуальный» признак, меньше — лучше", "доля",
                            f"nlp_false_actual_{data['split']}", out, ymax=1.1))
    figs.append(hbar(models, [data["models"][m]["all"]["latency"]["median"] for m in models], [None] * len(models), colors,
                     "Извлечение тегов: медианная задержка на запись, секунды", "секунды", f"nlp_latency_{data['split']}", out,
                     fmt="{:.2f}"))
    return lines + [f"![]({f})" for f in figs] + [""]


ARM_COLORS = {"free": SERIES[0], "v1": SERIES[1], "freefb": SERIES[2 % len(SERIES)], "slots": SERIES[3 % len(SERIES)],
              "freenq": SERIES[4 % len(SERIES)], "freefbnq": SERIES[0],
              "template": BASELINE_COLOR}


def overview_section(data: Dict[str, Any], review: Optional[Dict[str, Any]], out: Path) -> List[str]:
    systems = list(data["models"])
    metrics = {name: data["models"][name]["all"] for name in systems}
    colors = {name: ARM_COLORS.get(name.rsplit("-", 1)[-1], BASELINE_COLOR) for name in systems}
    lines = [f"## Обзор для PDF ({data['split']})", "",
             f"Периодов: {data['gold_rows']}, утверждено человеком: {data['gold_approved']}. Системы: `template` — шаблон; "
             "`<модель>-v1` — модель выбирает и группирует готовые формулировки; `<модель>-free` — модель пишет связный текст, "
             "сервер проверяет числа, даты, цитаты и запрещённые обороты. При отказе проверки публикуется шаблон.", ""]
    rows = []
    for name, x in metrics.items():
        human = (review or {}).get(name, {})
        rows.append([name, str(x["n"]), num(x["valid_first_rate"], 2), num(x["valid_final_rate"], 2), num(x["fallback_rate"], 2),
                     num(x["required_coverage_published"], 2), num(x["required_coverage_valid_llm"], 2),
                     num(x["claims_mean"], 1), num(x["chars_mean"], 0), num(x["latency"]["median"], 2),
                     num(human.get("clarity_mean"), 2), num(human.get("unsupported_rate"), 2)])
    lines.append(table(["Система", "n", "Принят с 1-й попытки", "Принят после повтора", "Откат на шаблон", "Охват важных фактов",
                        "Охват (только принятые LLM)", "Пунктов", "Знаков", "Медиана, с", "Понятность 1–5", "Неподдержанные, доля"], rows))
    lines.append("")
    reasons = {name: x["rejection_reasons"] for name, x in metrics.items() if x["rejection_reasons"]}
    if reasons:
        lines += ["Причины отклонения первого ответа:", ""]
        lines += [f"- `{name}`: " + ", ".join(f"{k} — {v}" for k, v in sorted(r.items())) for name, r in reasons.items()] + [""]
    figs = [hbar(systems, [metrics[m]["valid_final_rate"] for m in systems], [None] * len(systems), colors,
                 "Обзор: доля ответов, принятых проверкой после повтора, больше — лучше", "доля",
                 f"overview_valid_{data['split']}", out, limit=1.0),
            hbar(systems, [metrics[m]["required_coverage_published"] for m in systems], [None] * len(systems), colors,
                 "Обзор: охват важных фактов в опубликованном тексте, больше — лучше", "доля",
                 f"overview_coverage_{data['split']}", out, limit=1.0),
            hbar(systems, [metrics[m]["chars_mean"] for m in systems], [None] * len(systems), colors,
                 "Обзор: средняя длина опубликованного текста, знаков", "знаков", f"overview_length_{data['split']}", out,
                 fmt="{:.0f}")]
    return lines + [f"![]({f})" for f in figs] + [""]


def build(results: Path = RESULTS) -> Path:
    config = load_config()
    llm_order = [m["id"] for m in config["llm_models"]]
    asr_order = [m["id"] for m in config["asr_models"]]
    out = results / "figures"
    document = ["# Результаты исследования", "",
                "Файл создаётся скриптом `python3 -m study.report` из `results/metrics/*.json`; вручную не редактируется. "
                "Цифры — только измеренные; пустые разделы означают, что прогон ещё не выполнялся.", ""]
    for split in ("dev", "test"):
        path = results / "metrics" / f"asr__{split}.json"
        if path.exists():
            data = read_json(path)
            document += asr_section(data, colors_for(list(data["models"]), asr_order), out)
        path = results / "metrics" / f"nlp__{split}.json"
        if path.exists():
            data = read_json(path)
            document += nlp_section(data, colors_for(list(data["models"]), llm_order), out)
        path = results / "metrics" / f"report__{split}.json"
        if path.exists():
            review_path = results / "metrics" / f"report_review__{split}.json"
            document += overview_section(read_json(path), read_json(review_path) if review_path.exists() else None, out)
    target = results / "REPORT.md"
    target.write_text("\n".join(document) + "\n", encoding="utf-8")
    return target


def main() -> None:
    argparse.ArgumentParser(description="Собрать REPORT.md и графики из метрик").parse_args()
    print(build())


if __name__ == "__main__":
    main()
