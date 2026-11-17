# HTTP API

Краткий справочник. Полный контракт (схемы, коды ошибок) — OpenAPI/Swagger: профиль `local`, `/swagger-ui.html`. Здесь только соглашения и сводка эндпоинтов; решения — в [architecture.md](architecture.md).

## Соглашения

- Префикс `/api/v1`, REST + JSON, UUID-идентификаторы. Все ресурсы принадлежат пользователю из JWT; `userId` в запросах не передаётся. Чужой или отсутствующий ресурс — одинаковый 404.
- Время: дата `2026-10-09`, абсолютное время ISO 8601; входной `occurredAt` содержит offset, `timeZone` — IANA, согласованность проверяется.
- Оценки — целое 0–10 или `null`. Доза — JSON-число (`12.5`, не строка: в Jackson выключено приведение скаляров, строка даёт 400) с явной единицей (MG/G/ML/TABLET/DROP/UNIT).
- Списки: `{items, page, size, totalElements, totalPages}`, size по умолчанию 20, максимум 100. Лента: `occurredAt DESC, id DESC`.
- Диапазоны `from`/`to` — включительные локальные даты в указанной зоне, ≤ 366 дней (плановые приёмы ≤ 31 дня за запрос).
- **Версии:** изменение/удаление существующего ресурса требует `If-Match: "vN"` (нет — 428, устарел — 412). Ответы изменяемых ресурсов содержат `ETag`. `version` защищает от потери правок, `revision` — неизменяемая версия текста записи.
- **Идемпотентность:** создающие POST/PUT требуют `Idempotency-Key` (UUID операции клиента). Тот же ключ и тело → прежний результат; другое тело → 409 `IDEMPOTENCY_CONFLICT`; повтор после удаления → 410.
- **Ошибки:** `application/problem+json` (`code`, `fieldErrors`, `traceId`). 400 валидация, 401 нет входа, 403 запрещено настройками (AI выключен/нет согласия), 404, 409 конфликт, 410 удалено, 412/428 версии, 413 размер, 415 тип, 429 лимит, 503 компонент недоступен. SQL, stack trace и текст дневника в ошибки не попадают.
- Асинхронные операции: 202 + `Location`; сбой задачи — GET остаётся 200 со статусом FAILED.
- После любого изменения наблюдений, тегов, видимости, схем или приёмов растёт `dataRevision` пользователя; отчёты и экспорты старой ревизии стираются/устаревают.

## Эндпоинты

| Группа | Методы |
| --- | --- |
| Вход | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Профиль | `GET/PATCH /me`, `GET/PATCH /me/settings` (aiEnabled, согласия, напоминания, тема), `DELETE /me` (пароль + `confirm=true`) |
| Оценки | `GET /ratings`, `GET/PUT/DELETE /ratings/{date}/{slot}` |
| Записи | `POST/GET /entries` (поиск `q`, период, `confirmedTag`), `GET/PATCH/DELETE /entries/{id}` |
| Календарь | `GET /calendar`, `GET /days/{date}` |
| Теги | `GET /tag-definitions`, `PUT /entries/{id}/tag-review` (полная замена разметки текущей revision) |
| AI-анализ | `POST /entries/{id}/analyses`, `GET .../analyses/{analysisId}`, `POST .../cancel` |
| Препараты | `POST/GET /medications`, `GET/PATCH/DELETE /medications/{id}`, `GET/POST /medications/{id}/schedule-versions`, `POST /medications/{id}/archive` |
| Приёмы | `GET /medication-occurrences`, `POST/GET /intakes`, `GET/PATCH/DELETE /intakes/{id}` |
| Аналитика | `GET /analytics/overview`, `GET /analytics/tag-comparisons` |
| Отчёты | `POST/GET /reports`, `GET/DELETE /reports/{id}`, `GET /reports/{id}/pdf`, `POST /reports/{id}/cancel` |
| Экспорт | `POST /data-exports`, `GET/DELETE /data-exports/{id}`, `GET /data-exports/{id}/file` |
| Голос | `GET/PUT /speech/consent`, `POST /speech/transcriptions` (multipart `audio`), `GET/DELETE /speech/transcriptions/{id}`, `POST .../cancel` |
| Служебное | `/actuator/health` (ограниченный) |

## Ключевые контракты

**Теги.** Кандидат: `candidateId, labelCode, category, quote, start, end, presence, temporal, subject` (UTF-16). Тело `tag-review`: `{revision, complete, decisions:[{candidateId, action: ACCEPT|EDIT|REJECT|PENDING, appliesToLocalDate, replacement?}], manualTags:[...]}` — полная замена; при `complete=true` PENDING запрещён; дата нужна для участия в аналитике. Анализ статусов: QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED/STALE. Устаревшая revision — 409 `STALE_REVISION`; повторный анализ завершённого ревью — 409 `REVIEW_ALREADY_COMPLETED`; ответ задачи содержит `modelId, promptVersion, schemaVersion`.

**Препараты.** Новая схема: `effectiveFrom` (instant) строго после последней версии и не раньше текущего времени сервера (иначе 400 «Нельзя изменять расписание задним числом»; клиент отправляет «сейчас + 30 с» и сразу показывает новую схему текущей), `treatmentEndsOn` (последняя дата лечения в зоне схемы), `timeZone`, `slots:[{localTime, daysOfWeek, dose, unit}]`; нарушение истории — 409 `SCHEDULE_HISTORY_CONFLICT`. Приём: PLANNED (`occurrenceId`, TAKEN/SKIPPED; сервер сверяет слот, а не доверяет переданной дозе) или UNPLANNED; повторная отметка — 409 `INTAKE_ALREADY_EXISTS`; снятие отметки — DELETE. Статусы доз: `TAKEN/SKIPPED/NO_DATA`.

**Аналитика.** Overview: ряды, заполненность по слотам, дневные средние со счётчиком слотов, частоты тегов (упоминания и дни отдельно), выполнение/покрытие схем, дни высокой тревоги (порог по умолчанию 7), ограничения. Сравнение: группы `withMention/withoutMention` (`nDays, mean, dates`), `excluded`, `status SUFFICIENT/INSUFFICIENT_DATA`, `delta` только при ≥ 5 дней в обеих группах.

**Отчёт.** Запрос: период, зона, `displayName`, `sections` (RATINGS/TAGS/MEDICATIONS/NOTES/AI_OVERVIEW), `entrySelection` (ALL_VISIBLE/EXPLICIT/NONE) + `entryIds`, `medicationSelection` + `medicationIds`, `comparisonTagCodes` (≤ 12). Скрытая запись при явном ID — 409 `SOURCE_NOT_REPORTABLE`. Ответ: статус QUEUED/RUNNING/READY/FAILED/CANCELLED, `stale`, `overviewMode` AI/TEMPLATE/NONE, `facts`, `overview.claims[{id, text, factIds, sourceRefs}]` (`text` — связный текст модели, проверенный сервером; `sourceRefs` вычисляет сервер), `previewData`. `/pdf`: устаревший — 409 `REPORT_STALE`, не готов — 409 `REPORT_NOT_READY`; `includeAiOverview=false` скрывает блок. Включить раздел AI без согласия — 403 до старта. PDF отправляет клиент (системный Share).

**Экспорт.** ZIP с `diary.json` и описанием формата; срок 24 часа, просрочен/очищен — 410.

**Голос.** WAV PCM16 mono 16 kHz ≤ 120 с / 4 MiB; 403 нет согласия, 409 уже есть активная задача, 413 размер, 415 неверный WAV, 429 лимит, 503 ASR не настроен. Согласие: `PUT /speech/consent` с `If-Match` и `{enabled, consentVersion:"diary-speech-v1"}`; требуется включённый общий AI (`diary-ai-v1`).

## На стороне клиента (не API)
Локальный черновик и статус сохранения, автозапуск AI-анализа сразу после сохранения записи (отдельный `POST /entries/{id}/analyses`, если AI включён; бэкенд сам анализ не стартует), локальные уведомления по occurrences, системный Share, защищённое хранение токенов, запись и кодирование WAV, выделение UTF-16 фрагментов, обработка 412. HealthKit, Android и полная офлайн-синхронизация вне объёма.
