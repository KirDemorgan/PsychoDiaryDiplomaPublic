# Бэкенд

Модульный монолит на Java 25 / Spring Boot 4.1.1 для iPhone-клиента. Модули: авторизация, профиль и настройки, оценки, записи, препараты и приёмы, теги, календарь и аналитика, отчёты и PDF, экспорт и удаление аккаунта, очередь AI-задач, голосовой ввод. Решения — [architecture](../docs/backend/architecture.md), API — [api](../docs/backend/api.md).

## Стек

Версии заданы в `pom.xml`, остальные управляются Spring Boot BOM.

| Библиотека | Версия | Зачем |
| --- | --- | --- |
| Spring Boot (MVC, Security/Resource Server, Data JPA, Validation, Actuator) | 4.1.1 | Основа, HTTP, JWT, БД |
| PostgreSQL JDBC, Liquibase | BOM | БД и миграции |
| MapStruct / Lombok / lombok-mapstruct-binding | 1.6.3 / 1.18.48 / 0.2.0 | Маппинг и шаблонный код |
| springdoc-openapi | 3.1.1 | OpenAPI, Swagger UI (профиль `local`) |
| Thymeleaf, OpenHTMLtoPDF, PDFBox | BOM / 1.1.97 / 3.0.8 | PDF отчёта |
| JUnit, Mockito, AssertJ, Testcontainers | BOM | Тесты |
| JaCoCo | 0.8.15 | Покрытие, порог 80% строк |

## Запуск

1. Поднять PostgreSQL: см. [infra/README.md](../infra/README.md).
2. Задать переменные окружения из `.env.example` (Spring `.env` сам не читает — через Run Configuration IDEA или shell). Нужны `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_BASE64` (минимум 32 случайных байта в Base64). Для моделей: `LLM_ENDPOINT` + `LLM_MODEL_ID`, `SPEECH_ENDPOINT` + `SPEECH_MODEL_ID` (пары: обе или ни одной) и необязательные токены.
3. Профиль `local` включает Swagger. Только синтетические данные: для локального запуска транспорт — HTTP; развёрнутый сервер работает по HTTPS.

Миграции применяет Liquibase, Hibernate лишь проверяет соответствие (`ddl-auto=validate`). Без настроенной модели сервис работает: теги вводятся вручную, обзор строится шаблоном.

## Проверки (из каталога `backend/`)

```shell
mvn clean verify                          # unit + MockMvc, порог JaCoCo 80%
mvn clean verify -Pintegration-tests      # + Testcontainers PostgreSQL, нужен Docker
```

Workflow `../.github/workflows/backend-ci.yaml` запускает интеграционный профиль на PR/push в `master`. Фактические результаты и ограничения запусков — в [architecture](../docs/backend/architecture.md) (раздел «Проверка и CI»).

## Организация кода

Базовый пакет `alex.drankov.diary`; в каждом модуле `controller / dto / service / entity / repository / mapper`. Публичные DTO — отдельные файлы, сущности наружу не отдаются. Общий код в `common` не импортирует бизнес-модули. Контроллер принимает типизированный запрос, сервис владеет правилами и транзакцией, репозиторий работает с БД.

## Соглашения

`.editorconfig` задаёт форматирование. В тестах `@DisplayName` на русском, параметризованные — с `name = ...`. Комментарии — только для неочевидных ограничений. При изменении поведения обновлять [architecture](../docs/backend/architecture.md) и [api](../docs/backend/api.md).
