# research/

Код и данные для выбора моделей: ASR и одной текстовой LLM (извлечение тегов). Критерии выбора заданы в `configs/study.yaml`, итоговые метрики — в `results/REPORT.md`.

## Устройство

```text
research/
├── study/            код: метрики, раннеры, студия датасета, отчёт
├── configs/study.yaml   модели, параметры Ollama, критерии выбора
├── data/
│   ├── texts.jsonl      тексты дневника с эталоном (в Git)
│   ├── periods.jsonl    35 синтетических периодов с пакетами фактов для обзора (в Git)
│   ├── audio.jsonl      описание записей и эталон расшифровки (в Git)
│   └── audio/           WAV 16 кГц mono (НЕ в Git, переносится копированием)
├── results/             ответы моделей (jsonl), metrics/, figures/, REPORT.md
├── tests/               тесты метрик и обвязки
└── datasets/local/      старый корпус Piper (в Git не входит)
```

Метрики, студия и отчёт не требуют GPU. Модели нужны только раннерам: ASR (Whisper, GigaAM, T-one) и Ollama (LLM). Всё это запускается **внутри WSL2** на Windows-машине с RTX 4060: T-one требует Linux, а Ollama, Python и ASR в одном окружении не требуют ни сетевых настроек, ни разных интерпретаторов. Нужен драйвер NVIDIA для Windows с поддержкой WSL (проверка: `nvidia-smi` в WSL). Память WSL: `memory=12GB`, `swap=8GB` в `%UserProfile%\.wslconfig`, затем `wsl --shutdown`.

## Установка

```shell
cd research
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt          # метрики, студия, отчёт
pip install -r requirements-asr.txt      # только там, где крутятся ASR-модели
python3 -m pytest -q                      # тесты метрик
```

## Порядок работы

1. **Данные.** `python3 -m study.make_data seed-texts` создаёт шаблонные тексты и сложные случаи; `import-legacy-audio` переносит синтетические WAV (Piper), оставляя записи до 25 с. Затем в студии — свои тексты и голос:
   ```shell
   streamlit run study/studio.py
   ```
   Разделы: запись голоса (карточки), новый текст с ручной разметкой, проверка и утверждение эталонов, прогресс. LLM в разметке не участвует.
2. **Прогон моделей** (внутри WSL2 на Windows-машине). Каждый ответ дописывается в `results/<задача>/<модель>__<split>.jsonl`, прерванный прогон продолжается с того же места (`--retry-errors` повторяет сбои).
   ```shell
   python3 -m study.run_asr --model whisper-turbo --split dev --limit 3     # проверка на 3 записях
   python3 -m study.run_asr --model whisper-turbo --split dev
   python3 -m study.run_nlp --model rules --split dev                       # baseline без модели
   python3 -m study.run_nlp --model qwen3.5-9b --split dev                  # нужен запущенный Ollama
   ```
   Для `--split test` нужны утверждённые эталоны (иначе скрипт остановится; `--allow-draft` даёт результат, который нельзя называть независимым).
   Обзор для PDF: шаблон считается без модели, LLM — в двух режимах (`v1` — выбор готовых формулировок, `free` — связный текст с проверкой):
   ```shell
   python3 -m study.periods                                                   # 35 периодов с пакетами фактов
   python3 -m study.run_report --arm template --split dev
   python3 -m study.run_report --arm free --model qwen3.5-9b --split dev
   python3 -m study.run_report --arm v1   --model qwen3.5-9b --split dev
   python3 -m study.review export --split dev                                # слепая выгрузка обзоров для ручной оценки
   python3 -m study.review import --split dev                                # после заполнения CSV
   ```
3. **Метрики и отчёт.** Модели не вызываются, считается по сохранённым ответам:
   ```shell
   python3 -m study.evaluate asr --split dev
   python3 -m study.evaluate nlp --split dev
   python3 -m study.evaluate report --split dev
   python3 -m study.report          # results/REPORT.md + results/figures/*.png
   ```

## Первый запуск на Windows-машине (WSL2)

1. В WSL2 (Ubuntu) перейдите в клонированный репозиторий и поставьте всё одной командой (один раз, нужен пароль sudo):
   ```shell
   bash research/scripts/setup_wsl.sh
   ```
   Скрипт ставит системные пакеты, окружение `~/venvs/diary`, GigaAM и T-one, Ollama и записывает версии в `results/environment/`.
2. Скопируйте в `research/data/` свои данные (`texts.jsonl`, `audio.jsonl`, папку `audio/`; `periods.jsonl` уже в Git).
3. Вечером одна команда: проверка окружения, пробный запуск каждой модели и сам ночной прогон:
   ```shell
   bash research/scripts/night.sh
   ```
   Сначала `study.check` печатает таблицу проверок и качает веса. Если есть блокирующая проблема (нет GPU, данных, Ollama), ночной прогон не стартует: перед сном дождитесь строки «Можно запускать ночной прогон» (она появится через 10–60 минут из-за загрузок). Если пишет «НЕ готово», пришлите мне вывод.
4. Утром смотрите `results/overnight/<время>/SUMMARY.md` и `results/REPORT.md`.

## Ночной прогон одной командой

```shell
python3 -m study.overnight --split dev
```

Скрипт сам: проверяет данные (нет ли WAV, `periods.jsonl`), доступность Ollama и загруженность моделей; для каждой модели запускает пробные 3 примера и, если они прошли, полный прогон; каждую задачу ведёт отдельным процессом (сбой одной модели не останавливает остальные); в конце считает метрики и собирает `results/REPORT.md`. Итог и логи — в `results/overnight/<время>/SUMMARY.md`. Порядок: шаблонные задачи, ASR, затем LLM по одной (теги, обзор `free`, обзор `v1`). Если прервали, запустите заново: готовые ответы пропускаются. `--dry-run` показывает план и проблемы без запуска. С флагом `--pull` недостающие LLM докачиваются сами (по очереди, перед первой задачей модели; нужно не менее 15 ГБ свободного места; каждая модель занимает 3–8 ГБ), без него такие модели пропускаются. После всех задач модели Ollama выгружает её из видеопамяти. Модели не удаляются. Веса ASR (Whisper, GigaAM, T-one) скачиваются при первой загрузке сами, нужен интернет. GigaChat докачать так нельзя: у него нет официального тега Ollama, импорт GGUF делается вручную.

Всё запускается из одного окружения WSL2. Чтобы ночью компьютер не уснул, отключите спящий режим на время прогона.

Для **test** одной командой запускаются только финалисты и только при утверждённых эталонах: `python3 -m study.overnight --split test --models qwen3.5-9b,whisper-turbo`. Без `--models` скрипт откажется, это защита от траты независимой оценки.

## Версии промптов и архив прогонов

Системные промпты (`ENTRY_PROMPT`, `REPORT_PROMPT`) лежат в `AiWorker.java`; исследование читает их оттуда (`study.common.backend_prompt`), поэтому сервер и эксперимент не расходятся. Версии: извлечение `entry-extraction-v2`, обзор `report-overview-v3`. Результаты dev-прогона на первых промптах (v1/v2) сохранены в `results/archive/dev-2026-10-18-prompt-v1/` для сравнения до и после настройки; метрики считаются только по файлам верхнего уровня `results/nlp`, `results/report`.

## Форматы

`texts.jsonl`: `id, group_id, split, origin (template|challenge|hand), scenario, text, events[{labelCode, quote, occurrence, presence, temporal, subject}], review_status (draft|approved), reviewer`.
`audio.jsonl`: `id, group_id, split, origin (tts|human), speaker_id, condition, audio_path, audio_seconds, reference_text, critical (список вариантов написания), review_status`.
Разделение dev/test — по группам (одна группа целиком в одном split); для записей из студии оно детерминированное по хешу.

## Что известно о данных

- Шаблонные тексты (`template`) порождены генератором: простой словарь с правилами набирает на них macro-F1 около 0,99, на сложных случаях (`challenge`) около 0,61. Поэтому срез `template` слабо различает модели, а главным срезом должен быть `hand` (тексты, написанные и размеченные вручную).
- Синтетические WAV (Piper TTS, два голоса) не заменяют живую речь: для вывода о диктовке нужны записи из студии (`origin: human`).
- Клипы длиннее 25 с исключены (GigaAM `transcribe` принимает до 25 с).
- Эталоны текстов, аудио и периодов утверждены автором до запуска на test.

## Состояние

Метрики ASR и NLP, доверительные интервалы, проверка ответов моделей и правила базового варианта покрыты тестами (`python3 -m pytest -q`). Ответы моделей, метрики и графики dev и test лежат в `results/`, сводка — `results/REPORT.md`.
