#!/usr/bin/env bash
# Установка окружения исследования в WSL2 (Ubuntu 22.04 / 24.04). Запускать один раз:
#   bash research/scripts/setup_wsl.sh
# Ставит системные пакеты, Python-окружение ~/venvs/diary, библиотеки ASR (GigaAM, T-one) и Ollama.
# Веса моделей скачиваются позже: ASR при первой загрузке, LLM командой study.check --pull / study.overnight --pull.
set -euo pipefail

RESEARCH="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV="$HOME/venvs/diary"
SRC="$HOME/src"
say() { printf '\n== %s\n' "$*"; }

say "WSL и видеокарта"
grep -qi microsoft /proc/version || echo "ВНИМАНИЕ: похоже, это не WSL2"
if command -v nvidia-smi >/dev/null 2>&1; then
    nvidia-smi --query-gpu=name,memory.total --format=csv,noheader
else
    echo "ОШИБКА: nvidia-smi не найден. Установите драйвер NVIDIA для Windows (с поддержкой WSL) и перезапустите WSL: wsl --shutdown"
    exit 1
fi

say "Системные пакеты (потребуется пароль sudo)"
sudo apt-get update
sudo apt-get install -y python3 python3-venv python3-dev python3-pip git curl zstd ffmpeg libsndfile1 \
    build-essential cmake zlib1g-dev libbz2-dev liblzma-dev

say "Python-окружение $VENV"
python3 -m venv "$VENV"
# shellcheck disable=SC1091
source "$VENV/bin/activate"
python -m pip install --upgrade pip wheel
pip install -r "$RESEARCH/requirements.txt"
pip install -r "$RESEARCH/requirements-asr.txt"

say "GigaAM и T-one"
mkdir -p "$SRC"
clone() {
    if [ -d "$2/.git" ]; then git -C "$2" pull --ff-only; else git clone "$1" "$2"; fi
}
clone https://github.com/salute-developers/GigaAM.git "$SRC/GigaAM"
pip install -e "$SRC/GigaAM[torch]"
clone https://github.com/voicekit-team/T-one.git "$SRC/T-one"
pip install -e "$SRC/T-one"

say "Ollama"
if ! command -v ollama >/dev/null 2>&1; then
    curl -fsSL https://ollama.com/install.sh | sh
fi

say "Фиксация версий"
mkdir -p "$RESEARCH/results/environment"
{
    date -u +"%Y-%m-%dT%H:%M:%SZ"
    echo "GigaAM $(git -C "$SRC/GigaAM" rev-parse HEAD)"
    echo "T-one $(git -C "$SRC/T-one" rev-parse HEAD)"
    ollama --version 2>&1 | tail -1
    python --version
} > "$RESEARCH/results/environment/versions.txt"
pip freeze > "$RESEARCH/results/environment/pip-freeze.txt"
cat "$RESEARCH/results/environment/versions.txt"

say "Готово"
echo "Дальше: bash $RESEARCH/scripts/night.sh"
echo "Если память WSL мала, в Windows в файле %UserProfile%\\.wslconfig задайте memory=12GB и swap=8GB, затем wsl --shutdown."
