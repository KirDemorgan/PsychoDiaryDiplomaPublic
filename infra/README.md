# Локальная инфраструктура

Compose содержит PostgreSQL 18.6 и опциональный Ollama 0.40.3 (профиль `ai`). Spring Boot запускается из IDEA/Maven на хосте; миграции выполняет Liquibase приложения. ASR подключается отдельным endpoint выбранного inference-сервиса; контейнер с невыбранной речевой моделью не объявлен работающим.

Скопируйте `infra/.env.example` в `infra/.env`, задайте `DB_PASSWORD`. Для Compose переменные читаются явно:

```sh
docker compose --env-file infra/.env -f infra/compose.yaml config
docker compose --env-file infra/.env -f infra/compose.yaml up -d postgres
```

Опциональный контейнер LLM:

```sh
docker compose --env-file infra/.env -f infra/compose.yaml --profile ai up -d
# После выбора модели загрузите её точный tag:
docker compose --env-file infra/.env -f infra/compose.yaml exec ollama ollama pull <selected-model:tag>
```

Модель автоматически не скачивается и не выбирается. `ollama list` в healthcheck проверяет процесс, а не наличие/качество нужной модели. PostgreSQL хранит данные в named volume на `/var/lib/postgresql`, как требуется [официальному образу PostgreSQL 18+](https://hub.docker.com/_/postgres). Имена контейнеров не фиксируются; порты публикуются только на loopback. Реальные секреты и данные в Git не включаются.

Для Spring перенесите значения из `backend/.env.example` в environment Run Configuration IDEA или экспортируйте их в shell. Spring сам не читает `.env`; переменные Compose также не передаются автоматически хостовому Java-процессу.

| Переменная backend | Значение для локального подключения |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://127.0.0.1:5432/diary` (учитывать DB_PORT) |
| `DB_USERNAME` | `diary` |
| `DB_PASSWORD` | Тот же локальный пароль, что в infra/.env |
| `JWT_SECRET_BASE64` | Собственный base64 секрет минимум 32 случайных байта |
| `LLM_ENDPOINT` | `http://127.0.0.1:11434/api/chat` (учитывать OLLAMA_PORT) |
| `LLM_MODEL_ID` | Точный загруженный tag, включая `:tag`; один для тегов и обзора PDF |
| `LLM_TOKEN` | Необязательный Bearer для защищённого proxy; локальный Ollama его не проверяет |
| `SPEECH_ENDPOINT` | Полный endpoint сервиса WAV → JSON `{text,modelId}` |
| `SPEECH_MODEL_ID` | Точный идентификатор выбранного ASR |
| `SPEECH_TOKEN` | Необязательный Bearer речевого сервиса |

Для запуска из IDE используйте профиль `local` только с синтетическими данными. Пара пустых endpoint/modelId отключает соответствующую интеграцию; неполная пара отклоняется при старте. Для двух уже подготовленных совместимых inference-сервисов достаточно задать endpoint/modelId и при необходимости токены; интерфейс Ollama и контракт ASR различаются. У произвольной модели нет универсального URL.

На Mac для измерений можно использовать нативный Ollama и этот же HTTP endpoint: [GPU acceleration в Docker Desktop для macOS недоступна](https://docs.ollama.com/faq). Контейнерный профиль предназначен для воспроизводимого CPU-запуска; приемлемую скорость/память сначала измеряют. Cloud-функции контейнерного Ollama отключены через OLLAMA_NO_CLOUD; один загруженный LLM и один параллельный запрос ограничивают ресурсы. Дополнительный ASR также потребляет память; отсутствие OOM не подтверждалось запуском.

Named volumes сохраняются после обычного `down`; `down -v` удаляет данные и модели. Пароль существующей БД не меняется от замены DB_PASSWORD в .env: нужен отдельный контролируемый шаг изменения пароля. Внешние подключения к backend/моделям с реальными данными требуют HTTPS и настроенного доступа. Этот Compose является локальной конфигурацией, не production deployment.

## LLM на Windows-компьютере

## Деплой бэкенда на сервер

Workflow `backend-deploy.yaml` запускается после успешного `Backend CI` на push в `master`. Он спрашивает у сервера хэш развёрнутого коммита и пропускает деплой, если `backend/` с тех пор не менялся (`git diff`). Иначе образ собирается на GitHub, передаётся по SSH (`docker save | ssh`) и перезапускает только сервис `backend`; при непройденном `/actuator/health` возвращается предыдущий образ. Запуск вручную: `workflow_dispatch`, опция `force`.

Секреты репозитория: `DEPLOY_HOST`, `DEPLOY_SSH_KEY` (отдельный ключ только для деплоя), `DEPLOY_KNOWN_HOSTS`. На сервере в `/opt/diary` лежат `compose.yaml`, `Caddyfile`, `deploy.sh` (из `infra/deploy/`) и `.env` с `DB_PASSWORD`, `JWT_SECRET_BASE64`, `BACKEND_TAG`. Пользователь `deploy` в `authorized_keys` ограничен командой `deploy.sh` (forced command, без pty и проброса портов). Эти файлы на сервер ставятся вручную один раз: CI их не меняет.

## Модели на домашнем компьютере (Tailscale)

Сервер не видит компьютер за домашним роутером, а Ollama не имеет авторизации, поэтому наружу порты не открываются: сервер и компьютер соединяются частной сетью Tailscale.

```text
iPhone ──HTTPS──► сервер (Caddy → backend) ──Tailscale──► компьютер с GPU (WSL2)
                                                           ├─ Ollama  :11434  gemma4:e4b (теги и обзор)
                                                           └─ ASR     :8001   GigaAM v3 e2e (диктовка)
```

Порядок:

1. Установить Tailscale на компьютер и на сервер, войти в одну сеть, посмотреть адреса (`tailscale ip -4`). В ACL Tailscale разрешить доступ к портам 11434 и 8001 компьютера только серверу. Порты WSL2 по умолчанию не видны из сети Tailscale: либо включить `networkingMode=mirrored` в `.wslconfig` (Windows 11 22H2 и новее), либо пробросить порты Windows → WSL командой `netsh interface portproxy`; способ проверить `curl` с сервера (шаг 3).
2. На компьютере в WSL запустить Ollama, слушающую только tailscale-адрес, и ASR-сервис ([infra/asr/README.md](asr/README.md)):
   ```sh
   OLLAMA_HOST=<tailscale-ip-пк>:11434 OLLAMA_KEEP_ALIVE=30m ollama serve
   ollama pull gemma4:e4b
   ```
   Без `OLLAMA_KEEP_ALIVE` модель выгружается через 5 минут простоя, и первый запрос после паузы ждёт загрузку.
3. Проверить с сервера (адрес и токен свои):
   ```sh
   curl -s http://<tailscale-ip-пк>:11434/api/tags
   curl -s http://<tailscale-ip-пк>:8001/health
   docker run --rm --network diary_default curlimages/curl -s http://<tailscale-ip-пк>:8001/health
   ```
   Последняя команда показывает, что контейнеры тоже достают до компьютера.
4. На сервере обновить `compose.yaml` из `infra/deploy/` (CI его не меняет) и добавить в `/opt/diary/.env`:
   ```sh
   LLM_ENDPOINT=http://<tailscale-ip-пк>:11434/api/chat
   LLM_MODEL_ID=gemma4:e4b
   SPEECH_ENDPOINT=http://<tailscale-ip-пк>:8001/transcribe
   SPEECH_MODEL_ID=gigaam-v3-e2e
   SPEECH_TOKEN=<тот же ASR_TOKEN, что на компьютере>
   ```
   Затем `docker compose up -d --no-deps backend`. `LLM_NUM_CONTEXT=8192` и `LLM_NUM_PREDICT=4096` заданы по умолчанию: именно с ними измерялось качество; `LLM_TIMEOUT` по умолчанию PT30S, но на сервере выставлен `PT150S`: первая загрузка Gemma в GPU заняла 85 с (запрос целиком 115 с), а таймаут 30 с оборвал бы первый запрос после паузы (прогретая модель отвечает за несколько секунд). Чтобы холодных стартов не было, на компьютере можно задать `OLLAMA_KEEP_ALIVE=-1` (модель не выгружается). Пара endpoint/model-id задаётся вместе; пустая пара отключает функцию.

Если компьютер выключен или недоступен, обзор PDF откатывается на шаблон, а диктовка завершается ошибкой (запись текстом работает); дневник, оценки и препараты не затрагиваются. Тексты записей и аудио диктовки при включённых AI и голосе передаются на домашний компьютер по каналу Tailscale; ASR-сервис не сохраняет аудио и расшифровки (временный файл удаляется, текст не логируется), поведение журналов самого Ollama не проверялось.

