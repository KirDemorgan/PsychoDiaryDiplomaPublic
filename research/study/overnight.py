"""Ночной прогон: проверки перед стартом, пробный запуск каждой модели, полный прогон, метрики и отчёт.

Каждая задача выполняется отдельным процессом: сбой или нехватка памяти одной модели не останавливает остальные.
Результаты дописываются по ходу, поэтому прерванный прогон можно запустить заново: готовые ответы пропускаются.

    python3 -m study.overnight --split dev        # внутри WSL2: ASR (T-one требует Linux), Ollama и Python в одном окружении
    python3 -m study.overnight --split test --models qwen3.5-9b,whisper-turbo     # только финалисты, эталоны утверждены
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

import requests

from .common import DATA, RESULTS, ROOT, load_config, read_jsonl, write_json
from .overview import ARMS

SMOKE_LIMIT = 3
JOB_TIMEOUT_SECONDS = 4 * 3600
ERROR_RATE_WARNING = 0.2
PULL_TIMEOUT_SECONDS = 2 * 3600
MIN_FREE_DISK_GB = 15


@dataclass
class Job:
    name: str
    task: str
    module: str
    args: List[str]
    kind: str
    model: Optional[str] = None
    result_name: str = ""
    status: str = "pending"
    detail: str = ""
    seconds: float = 0.0
    rows: int = 0
    errors: int = 0
    log: str = field(default="", repr=False)


def plan(config: Dict[str, Any], split: str, only: Optional[List[str]], arms: Optional[List[str]] = None) -> List[Job]:
    """Сначала быстрые задачи без модели, затем ASR, затем LLM по одной модели: так Ollama не перегружает память."""
    def wanted(model: str) -> bool:
        return only is None or model in only

    jobs: List[Job] = [
        Job("nlp:rules", "nlp", "run_nlp", ["--model", "rules", "--split", split], "cheap", None, "rules"),
        Job("report:template", "report", "run_report", ["--arm", "template", "--split", split], "cheap", None, "template"),
    ]
    for asr in config["asr_models"]:
        if wanted(asr["id"]):
            jobs.append(Job(f"asr:{asr['id']}", "asr", "run_asr", ["--model", asr["id"], "--split", split], "asr", asr["id"], asr["id"]))
    for llm in config["llm_models"]:
        if not wanted(llm["id"]):
            continue
        base = ["--model", llm["id"], "--split", split]
        jobs.append(Job(f"nlp:{llm['id']}", "nlp", "run_nlp", base, "llm", llm["id"], llm["id"]))
        for arm in arms or ARMS:
            jobs.append(Job(f"report:{llm['id']}:{arm}", "report", "run_report", ["--arm", arm, *base], "llm", llm["id"],
                            f"{llm['id']}-{arm}"))
    return jobs


def command(job: Job, extra: List[str]) -> List[str]:
    return [sys.executable, "-m", f"study.{job.module}", *job.args, *extra]


def ollama_base(config: Dict[str, Any]) -> str:
    return config["ollama"]["endpoint"].split("/api/")[0]


def installed_models(config: Dict[str, Any]) -> Optional[set]:
    try:
        models = requests.get(ollama_base(config) + "/api/tags", timeout=10).json().get("models", [])
        return {m["name"] for m in models}
    except (requests.RequestException, ValueError):
        return None


def pull_model(config: Dict[str, Any], tag: str) -> Optional[str]:
    """Загружает модель через Ollama API. Возвращает текст ошибки или None."""
    free_gb = shutil.disk_usage(Path.home()).free / 2**30
    if free_gb < MIN_FREE_DISK_GB:
        return f"мало места на диске: {free_gb:.0f} ГБ, нужно не менее {MIN_FREE_DISK_GB}"
    try:
        response = requests.post(ollama_base(config) + "/api/pull", json={"model": tag, "stream": False},
                                 timeout=PULL_TIMEOUT_SECONDS)
        body = response.json()
        if response.status_code != 200 or body.get("error"):
            return str(body.get("error", response.status_code))[:200]
        return None
    except (requests.RequestException, ValueError) as error:
        return f"{type(error).__name__}: {error}"[:200]


def unload_model(config: Dict[str, Any], tag: str) -> None:
    """Освобождает видеопамять: Ollama выгружает модель сразу, а не через keep-alive."""
    try:
        requests.post(ollama_base(config) + "/api/generate", json={"model": tag, "keep_alive": 0}, timeout=30)
    except requests.RequestException:
        pass


def preflight(config: Dict[str, Any], jobs: List[Job], split: str, pull: bool = False) -> List[str]:
    """Возвращает список блокирующих проблем и помечает задачи, которые нельзя запускать."""
    problems: List[str] = []
    for name in ("texts.jsonl", "audio.jsonl", "periods.jsonl"):
        if not (DATA / name).exists():
            problems.append(f"нет файла data/{name}")
    audio = read_jsonl(DATA / "audio.jsonl")
    missing = [row["audio_path"] for row in audio if not (DATA / row["audio_path"]).exists()]
    if missing:
        problems.append(f"нет {len(missing)} WAV из audio.jsonl, например {missing[0]}: скопируйте папку data/audio")
    if split == "test":
        for name in ("texts.jsonl", "audio.jsonl", "periods.jsonl"):
            rows = [r for r in read_jsonl(DATA / name) if r["split"] == "test"]
            approved = sum(r.get("review_status") == "approved" for r in rows)
            if approved != len(rows):
                problems.append(f"test: в {name} утверждено {approved} из {len(rows)} эталонов")
    available = installed_models(config)
    tags = {m["id"]: m["tag"] for m in config["llm_models"]}
    for job in jobs:
        if job.kind != "llm":
            continue
        tag = tags[job.model]
        if available is None:
            job.status, job.detail = "skipped", "Ollama недоступен по адресу из configs/study.yaml"
        elif tag.startswith("TODO"):
            job.status, job.detail = "skipped", "тег модели не заполнен в configs/study.yaml"
        elif tag not in available and not pull:
            job.status, job.detail = "skipped", f"модель не загружена: ollama pull {tag} (или запуск с --pull)"
    return problems


def ensure_model(job: Job, config: Dict[str, Any], tags: Dict[str, str]) -> bool:
    """Для LLM-задач докачивает модель, если её нет (только когда preflight оставил задачу из-за --pull)."""
    if job.kind != "llm":
        return True
    installed = installed_models(config)
    if installed is not None and tags[job.model] not in installed:
        error = pull_model(config, tags[job.model])
        if error:
            job.status, job.detail = "failed_pull", f"не удалось загрузить {tags[job.model]}: {error}"
            return False
    return True


def release_model(jobs: List[Job], index: int, config: Dict[str, Any], tags: Dict[str, str]) -> None:
    """Выгружает модель из видеопамяти, когда следующая LLM-задача относится к другой модели."""
    job = jobs[index]
    if job.kind != "llm":
        return
    following = next((j for j in jobs[index + 1:] if j.kind == "llm" and j.status != "skipped"), None)
    if following is None or following.model != job.model:
        unload_model(config, tags[job.model])


def run_job(cmd: List[str], log_path: Path) -> int:
    with log_path.open("w", encoding="utf-8") as log:
        try:
            return subprocess.run(cmd, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, timeout=JOB_TIMEOUT_SECONDS).returncode
        except subprocess.TimeoutExpired:
            log.write("\nПревышено время задачи\n")
            return 124
        except FileNotFoundError as error:
            log.write(f"\nКоманда не найдена: {error}\n")
            return 127


def read_results(job: Job, split: str) -> List[Dict[str, Any]]:
    return read_jsonl(RESULTS / job.task / f"{job.result_name}__{split}.jsonl")


def execute(job: Job, split: str, smoke: int, directory: Path) -> None:
    started = time.perf_counter()
    safe = job.name.replace(":", "_")
    if smoke and job.kind != "cheap":
        code = run_job(command(job, ["--limit", str(smoke)]), directory / f"{safe}.smoke.log")
        ok = [row for row in read_results(job, split) if row.get("status") == "ok"]
        if code != 0 or not ok:
            job.status = "failed_smoke"
            job.detail = f"пробный запуск не удался (код {code}), см. {safe}.smoke.log"
            job.seconds = time.perf_counter() - started
            return
    code = run_job(command(job, []), directory / f"{safe}.log")
    rows = read_results(job, split)
    job.rows, job.errors = len(rows), sum(row.get("status") != "ok" for row in rows)
    job.seconds = time.perf_counter() - started
    if code != 0:
        job.status, job.detail = "failed", f"код {code}, см. {safe}.log"
    elif job.rows and job.errors / job.rows > ERROR_RATE_WARNING:
        job.status, job.detail = "done_with_errors", f"сбоев {job.errors} из {job.rows}"
    else:
        job.status = "done"


def finish(split: str, directory: Path) -> Dict[str, int]:
    codes = {}
    for task in ("asr", "nlp", "report"):
        codes[task] = run_job([sys.executable, "-m", "study.evaluate", task, "--split", split], directory / f"evaluate_{task}.log")
    codes["report"] = run_job([sys.executable, "-m", "study.report"], directory / "report.log")
    return codes


def summary(jobs: List[Job], problems: List[str], split: str, started: datetime) -> str:
    lines = [f"# Ночной прогон ({split})", "", f"Начало: {started:%Y-%m-%d %H:%M}, длительность: "
             f"{(datetime.now() - started).total_seconds() / 60:.0f} мин.", ""]
    if problems:
        lines += ["Блокирующие проблемы:", ""] + [f"- {p}" for p in problems] + [""]
    lines += ["| Задача | Статус | Ответов | Сбоев | Минут | Примечание |", "|---|---|---|---|---|---|"]
    lines += [f"| {j.name} | {j.status} | {j.rows} | {j.errors} | {j.seconds / 60:.1f} | {j.detail} |" for j in jobs]
    lines += ["", "Метрики и графики: `results/REPORT.md`. Логи задач лежат рядом с этим файлом."]
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description="Ночной прогон исследования")
    parser.add_argument("--split", choices=["dev", "test"], default="dev")
    parser.add_argument("--models", default="", help="через запятую; для test обязателен (финалисты)")
    parser.add_argument("--arms", default="", help="режимы обзора через запятую; для test обязателен (замороженные варианты)")
    parser.add_argument("--smoke-limit", type=int, default=SMOKE_LIMIT, help="0 отключает пробный запуск")
    parser.add_argument("--dry-run", action="store_true", help="только показать план и проверки")
    parser.add_argument("--pull", action="store_true", help="загрузить недостающие модели Ollama (несколько ГБ каждая)")
    args = parser.parse_args()

    config = load_config()
    only = [m.strip() for m in args.models.split(",") if m.strip()] or None
    if args.split == "test" and only is None:
        raise SystemExit("Для test укажите финалистов: --models id1,id2 (независимую оценку нельзя тратить на все модели).")
    arms = [a.strip() for a in args.arms.split(",") if a.strip()] or None
    if args.split == "test" and arms is None:
        raise SystemExit("Для test укажите замороженные режимы обзора: --arms freenq,... (остальные тратить на test нельзя).")
    unknown = sorted(set(arms or []) - set(ARMS))
    if unknown:
        raise SystemExit(f"Неизвестные режимы обзора: {', '.join(unknown)}; допустимы {', '.join(ARMS)}")
    jobs = plan(config, args.split, only, arms)
    tags = {m["id"]: m["tag"] for m in config["llm_models"]}
    problems = preflight(config, jobs, args.split, args.pull)
    started = datetime.now()
    directory = RESULTS / "overnight" / started.strftime("%Y%m%d-%H%M%S")
    directory.mkdir(parents=True, exist_ok=True)
    print(f"Задач: {len(jobs)}, пропущено на проверке: {sum(j.status == 'skipped' for j in jobs)}, блокирующих проблем: {len(problems)}")
    for problem in problems:
        print("  ПРОБЛЕМА:", problem)
    if problems or args.dry_run:
        for job in jobs:
            print(f"  {job.name}: {job.status} {job.detail}")
        (directory / "SUMMARY.md").write_text(summary(jobs, problems, args.split, started), encoding="utf-8")
        raise SystemExit(1 if problems else 0)

    for number, job in enumerate(jobs, 1):
        if job.status == "skipped":
            continue
        print(f"[{number}/{len(jobs)}] {job.name} ...", flush=True)
        if not ensure_model(job, config, tags):
            print(f"    {job.status} {job.detail}", flush=True)
            continue
        execute(job, args.split, args.smoke_limit, directory)
        release_model(jobs, number - 1, config, tags)
        print(f"    {job.status} ({job.seconds / 60:.1f} мин) {job.detail}", flush=True)
        (directory / "SUMMARY.md").write_text(summary(jobs, problems, args.split, started), encoding="utf-8")
    finish(args.split, directory)
    write_json(directory / "jobs.json", [{**j.__dict__, "log": ""} for j in jobs])
    (directory / "SUMMARY.md").write_text(summary(jobs, problems, args.split, started), encoding="utf-8")
    print(f"Готово. Сводка: {directory / 'SUMMARY.md'}; отчёт: {RESULTS / 'REPORT.md'}")


if __name__ == "__main__":
    main()
