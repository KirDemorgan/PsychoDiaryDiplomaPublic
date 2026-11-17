#!/usr/bin/env bash
# Проверка окружения и ночной прогон одной командой (внутри WSL2):
#   bash research/scripts/night.sh            # dev
#   bash research/scripts/night.sh test qwen3.5-9b,whisper-turbo   # test для финалистов
set -uo pipefail

RESEARCH="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPLIT="${1:-dev}"
MODELS="${2:-}"
# shellcheck disable=SC1091
source "$HOME/venvs/diary/bin/activate"
cd "$RESEARCH"

if ! curl -fsS http://127.0.0.1:11434/api/tags >/dev/null 2>&1; then
    echo "Запускаю ollama serve"
    mkdir -p results
    nohup ollama serve > results/ollama.log 2>&1 &
    for _ in $(seq 1 30); do
        curl -fsS http://127.0.0.1:11434/api/tags >/dev/null 2>&1 && break
        sleep 1
    done
fi

python3 -m study.check --pull --smoke || {
    echo "Проверка нашла блокирующие проблемы. Ночной прогон НЕ запущен."
    exit 1
}
if [ -n "$MODELS" ]; then
    python3 -m study.overnight --split "$SPLIT" --pull --models "$MODELS"
else
    python3 -m study.overnight --split "$SPLIT" --pull
fi
