# Актуальные PlantUML-схемы

PlantUML — основной формат технических схем, Excalidraw используется для обзорного листа. Один исходник описывает один аспект. Модели: Gemma 4 E4B (теги и обзор) и GigaAM v3 e2e (распознавание речи).

| Исходник | Представление | Содержание |
| --- | --- | --- |
| [PDF_generation.puml](PDF_generation.puml) | [SVG](PDF_generation.svg) | Последовательность: снимок, worker, предпросмотр, PDF, ошибки и удаление |
| [PDF_selection.puml](PDF_selection.puml) | [SVG](PDF_selection.svg) | Activity: доступ, повторы, выборка, исключения и расчёты |
| [AI_task_state.puml](AI_task_state.puml) | [SVG](AI_task_state.svg) | Статусы, lease, повтор, отмена и устаревание |
| [Voice_input.puml](Voice_input.puml) | [SVG](Voice_input.svg) | Activity: загрузка, ошибки/повторы, ASR, подтверждение текста и текстовый AI |
| [Voice_task_state.puml](Voice_task_state.puml) | [SVG](Voice_task_state.svg) | Статусы ASR, lease, отмена, TTL, отзыв согласия и очистка |
| [ERD_core.puml](ERD_core.puml) | [SVG](ERD_core.svg) | Пользователи, сессии, оценки, редакции и повторы |
| [ERD_domain.puml](ERD_domain.puml) | [SVG](ERD_domain.svg) | Препараты, теги, задачи и производные артефакты |
| [Components.puml](Components.puml) | [SVG](Components.svg) | Компоненты: клиент по слоям, модули сервера, PostgreSQL, хост моделей |
| [Deployment.puml](Deployment.puml) | [SVG](Deployment.svg) | Развёртывание: CI, автодеплой по SSH с откатом, Caddy и HTTPS, хост моделей |
| [AI_sequence.puml](AI_sequence.puml) | [SVG](AI_sequence.svg) | Последовательность извлечения тегов: очередь, валидация цитат, подтверждение |
| [Backend_classes.puml](Backend_classes.puml) | [SVG](Backend_classes.svg) | Ключевые классы AI, голоса и отчётов |
| [Use_cases.puml](Use_cases.puml) | [SVG](Use_cases.svg) | Варианты использования: пользователь, врач вне приложения, AI-сервис |
| [Daily_activity.puml](Daily_activity.puml) | [SVG](Daily_activity.svg) | Activity: ежедневный сценарий (оценки, запись, голос, теги, приём препаратов) |
| [Doctor_activity.puml](Doctor_activity.puml) | [SVG](Doctor_activity.svg) | Activity: подготовка отчёта для врача, откат обзора, устаревание, передача PDF |

Обзорная схема генерации PDF одним листом: [PDF_generation.excalidraw](../PDF_generation.excalidraw) и [SVG](../PDF_generation.svg). Шесть панелей: выбор источников, согласованный снимок и расчёты, проверка AI-обзора, предпросмотр и загрузка, PDF-шаблон, отмена и удаление. Зелёные блоки — реализованный сервер, синие — обязанности будущего клиента. С AI-обзором отчёт QUEUED с предварительным TEMPLATE_PREVIEW → READY с AI либо с проверяемым шаблоном и причиной; до READY PDF не выдаётся; устаревший снимок — `409 REPORT_STALE`.

Используются стабильные английские aliases, русские подписи, явные условия переходов, группы ответственности и минимальное оформление. Цвет дополняет смысл, а не заменяет подписи. ERD показывает значимые поля и основные связи; полный набор ограничений — миграции 001–008. Позиции цитат и статусы соответствуют серверному контракту. Направление стрелок ERD не означает передачу пользовательских данных.

Activity использует [актуальный синтаксис PlantUML](https://plantuml.com/activity-diagram-beta); состояния — [state diagram](https://plantuml.com/state-diagram), ERD — [Information Engineering](https://plantuml.com/ie-diagram). Goto и внешние include не применяются. Smetana позволяет рендерить ERD и состояния без отдельного Graphviz. JAR не входит в runtime бэкенда.

Схемы проверены PlantUML 1.2026.8 (синтаксис и SVG-рендеринг), имена таблиц, статусов и эндпоинтов сверены с кодом. Версия инструмента фиксируется для воспроизводимого экспорта:

```sh
java -Djava.awt.headless=true -jar /path/to/plantuml-1.2026.8.jar \
  -checkonly docs/diagrams/plantuml/*.puml
java -Djava.awt.headless=true -jar /path/to/plantuml-1.2026.8.jar \
  -tsvg -charset UTF-8 docs/diagrams/plantuml/*.puml
```

Исходники и SVG обновляются вместе.
