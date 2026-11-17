"""Проверка перед ночным прогоном: окружение, данные, Ollama и (с --smoke) пробный запуск каждой модели на 1 примере.

    python3 -m study.check --pull --smoke

Пробный запуск заодно скачивает веса ASR и (с --pull) LLM, поэтому ночью они уже на диске.
Код возврата 1 — блокирующая проблема (нет данных, GPU, Ollama и т. п.). Сбой отдельной модели не блокирует прогон:
ночной скрипт её пропустит и покажет причину в SUMMARY.md.
"""
from __future__ import annotations

import argparse
import importlib
import shutil
import subprocess
from pathlib import Path
from typing import Callable, List, Tuple

from . import overnight
from .common import DATA, RESULTS, load_config, read_jsonl

OK, WARN, FAIL = "OK", "ВНИМАНИЕ", "ОШИБКА"
Check = Tuple[str, str, str]


def check_gpu() -> Check:
    try:
        output = subprocess.run(["nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader"],
                                capture_output=True, text=True, timeout=20)
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return "GPU", FAIL, "nvidia-smi не найден: установите драйвер NVIDIA для Windows с поддержкой WSL"
    if output.returncode != 0:
        return "GPU", FAIL, output.stderr.strip()[:200]
    return "GPU", OK, output.stdout.strip().splitlines()[0]


def check_cuda_in_torch() -> Check:
    try:
        import torch
    except ImportError:
        return "torch", FAIL, "не установлен (setup_wsl.sh)"
    if not torch.cuda.is_available():
        return "torch CUDA", FAIL, "torch не видит GPU"
    return "torch CUDA", OK, f"{torch.__version__}, {torch.cuda.get_device_name(0)}"


def check_libraries() -> List[Check]:
    results = []
    for module, hint, fatal in (("soundfile", "pip install -r requirements.txt", True),
                                ("transformers", "pip install -r requirements-asr.txt", False),
                                ("gigaam", "setup_wsl.sh", False), ("tone", "setup_wsl.sh", False)):
        try:
            importlib.import_module(module)
            results.append((f"библиотека {module}", OK, ""))
        except Exception as error:  # ImportError и ошибки инициализации нативных библиотек
            # Отсутствие одной ASR-библиотеки не блокирует ночь: задачи этой модели будут пропущены.
            results.append((f"библиотека {module}", FAIL if fatal else WARN,
                            f"{type(error).__name__}: {str(error)[:120]} ({hint})"))
    return results


def check_resources() -> List[Check]:
    free_gb = shutil.disk_usage(Path.home()).free / 2**30
    results = [("диск", OK if free_gb >= 40 else WARN, f"свободно {free_gb:.0f} ГБ (модели занимают 15–25 ГБ)")]
    try:
        total = next(int(line.split()[1]) for line in Path("/proc/meminfo").read_text().splitlines()
                     if line.startswith("MemTotal")) / 2**20
        results.append(("память WSL", OK if total >= 11 else WARN,
                        f"{total:.0f} ГБ (рекомендуется 12 ГБ: .wslconfig memory=12GB, swap=8GB)"))
    except (OSError, StopIteration):
        pass
    return results


def check_data() -> List[Check]:
    results = []
    for name in ("texts.jsonl", "audio.jsonl", "periods.jsonl"):
        rows = read_jsonl(DATA / name)
        results.append((f"data/{name}", OK if rows else FAIL, f"{len(rows)} строк" if rows else "нет файла или он пуст"))
    audio = read_jsonl(DATA / "audio.jsonl")
    missing = [row["audio_path"] for row in audio if not (DATA / row["audio_path"]).exists()]
    results.append(("WAV-файлы", FAIL if missing else OK,
                    f"нет {len(missing)} из {len(audio)}, например {missing[0]}" if missing else f"все {len(audio)} на месте"))
    return results


def check_ollama(config) -> List[Check]:
    installed = overnight.installed_models(config)
    if installed is None:
        return [("Ollama", FAIL, "не отвечает; в WSL запустите: ollama serve")]
    results = [("Ollama", OK, f"доступен, моделей загружено: {len(installed)}")]
    for model in config["llm_models"]:
        tag = model["tag"]
        if tag.startswith("TODO"):
            results.append((f"модель {model['id']}", WARN, "тег не заполнен в configs/study.yaml (GigaChat: импорт GGUF вручную)"))
        elif tag not in installed:
            results.append((f"модель {model['id']}", WARN, f"не загружена ({tag}); --pull загрузит"))
        else:
            results.append((f"модель {model['id']}", OK, tag))
    return results


def smoke(config, pull: bool) -> List[Check]:
    """Один пример на каждую модель; пробный запуск скачивает веса и проверяет весь путь до результата."""
    results = []
    tags = {m["id"]: m["tag"] for m in config["llm_models"]}
    directory = RESULTS / "check"
    directory.mkdir(parents=True, exist_ok=True)
    for job in overnight.plan(config, "dev", None):
        if job.kind == "cheap" or (job.kind == "llm" and job.name.endswith(":v1")):
            continue
        if job.kind == "llm":
            if tags[job.model].startswith("TODO"):
                continue
            installed = overnight.installed_models(config) or set()
            if tags[job.model] not in installed:
                if not pull:
                    results.append((f"проба {job.name}", WARN, "модель не загружена"))
                    continue
                error = overnight.pull_model(config, tags[job.model])
                if error:
                    results.append((f"проба {job.name}", WARN, f"загрузка не удалась: {error}"))
                    continue
        log = directory / (job.name.replace(":", "_") + ".log")
        code = overnight.run_job(overnight.command(job, ["--limit", "1"]), log)
        ok = [row for row in overnight.read_results(job, "dev") if row.get("status") == "ok"]
        results.append((f"проба {job.name}", OK if code == 0 and ok else WARN,
                        "работает" if code == 0 and ok else f"не удалась, см. {log}"))
        if job.kind == "llm":
            overnight.unload_model(config, tags[job.model])
    return results


def main() -> None:
    parser = argparse.ArgumentParser(description="Проверка перед ночным прогоном")
    parser.add_argument("--smoke", action="store_true", help="пробный запуск каждой модели на 1 примере (скачивает веса)")
    parser.add_argument("--pull", action="store_true", help="загрузить недостающие LLM для пробы")
    args = parser.parse_args()
    config = load_config()
    checks: List[Check] = [check_gpu(), check_cuda_in_torch(), *check_libraries(), *check_resources(), *check_data(),
                           *check_ollama(config)]
    if args.smoke and not any(status == FAIL for _, status, _ in checks):
        checks += smoke(config, args.pull)
    width = max(len(name) for name, _, _ in checks)
    for name, status, detail in checks:
        print(f"{status:9} {name:<{width}}  {detail}")
    fatal = [name for name, status, _ in checks if status == FAIL]
    print()
    if fatal:
        print("К ночи НЕ готово. Исправьте:", ", ".join(fatal))
        raise SystemExit(1)
    warnings = [name for name, status, _ in checks if status == WARN]
    print("Можно запускать ночной прогон." + (f" Будут пропущены или частично недоступны: {', '.join(warnings)}." if warnings else ""))


if __name__ == "__main__":
    main()
