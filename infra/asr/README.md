# ASR-сервис (GigaAM v3 e2e)

Тонкий HTTP-сервис под контракт бэкенда (`HttpSpeechAdapter`): `POST` тела `audio/wav` (PCM16, моно, 16 кГц) с заголовками `Authorization: Bearer <токен>`, `X-Speech-Model: gigaam-v3-e2e`, `X-Speech-Language: ru`; ответ строго `{"text": "...", "modelId": "gigaam-v3-e2e"}`. Пустой текст означает тишину. Коды ошибок: 401 токен, 400 модель или язык, 415 не тот формат WAV или тип содержимого, 413 длиннее лимита или больше 4 МиБ, 500 сбой распознавания (без деталей). Тексты расшифровок в логи не пишутся, только длительность и время обработки.

Лимит записи 26 с (`ASR_MAX_SECONDS`): GigaAM проверялся только на записях до 25 с, секунда запаса учитывает задержку остановки записи в приложении; длинные записи — отдельное исследование (см. `docs/research/README.md`). Приложение временно ограничивает диктовку 25 с.

## Запуск на компьютере с моделями (WSL2)

```sh
source ~/venvs/diary/bin/activate        # то же окружение, где установлен GigaAM для исследования
pip install -r ~/Diploma/infra/asr/requirements.txt
cd ~/Diploma/infra/asr
export ASR_TOKEN=$(openssl rand -hex 24)  # сохраните: тот же токен нужен серверу как SPEECH_TOKEN
uvicorn --factory app:create_from_env --host <tailscale-ip-пк> --port 8001
```

Проверка на записи из датасета (подставьте имя файла):

```sh
curl -s -X POST http://<tailscale-ip-пк>:8001/transcribe -H "Authorization: Bearer $ASR_TOKEN" \
  -H "Content-Type: audio/wav" -H "X-Speech-Model: gigaam-v3-e2e" --data-binary @../../research/data/audio/<файл>.wav
```

Переменные: `ASR_TOKEN` (обязательна, без неё сервис не стартует), `ASR_MODEL_ID` (по умолчанию `gigaam-v3-e2e`, должен совпадать с `SPEECH_MODEL_ID` бэкенда), `ASR_MAX_SECONDS`, `ASR_GIGAAM_MODEL` (`v3_e2e_rnnt`). Распознаватель один на GPU, запросы обрабатываются по очереди.

## Тесты контракта (без GPU и без GigaAM)

```sh
pip install -r requirements-test.txt && python3 -m pytest -q
```
