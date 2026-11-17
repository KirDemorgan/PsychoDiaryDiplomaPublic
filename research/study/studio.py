"""Студия датасета: запись голоса, ввод и разметка текстов, утверждение эталонов. Модели не используются.

Запуск из каталога research/:  streamlit run study/studio.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import streamlit as st

from study.audio_io import to_wav
from study.facts import alias, sentence
from study.common import DATA, find_quote, read_jsonl, split_for_group, write_jsonl
from study.labels import LABELS, LABEL_RU, PRESENCE, SUBJECT, TEMPORAL, actual_labels, make_event
from study.voice_prompts import CONDITIONS, FREE, READ

TEXTS = DATA / "texts.jsonl"
AUDIO = DATA / "audio.jsonl"
PERIODS = DATA / "periods.jsonl"
MAX_SECONDS = 25
SCENARIOS = ["current", "negation", "past", "future", "uncertain", "other", "colloquial", "typo", "multi",
             "contradiction", "no_target", "injection", "emoji", "nonsense"]


def highlight(text: str, events) -> str:
    spans = []
    for event in events:
        start = text.find(event["quote"])
        if start >= 0:
            spans.append((start, start + len(event["quote"]), event))
    spans.sort(key=lambda item: item[0])
    out, cursor = [], 0
    for start, end, event in spans:
        if start < cursor:
            continue
        out.append(text[cursor:start])
        tag = f'{LABEL_RU[event["labelCode"]]} · {event["presence"]} · {event["temporal"]} · {event["subject"]}'
        out.append(f'<mark title="{tag}">{text[start:end]}</mark> <small>[{tag}]</small>')
        cursor = end
    out.append(text[cursor:])
    return "".join(out).replace("\n", "<br>")


def event_form(text: str, key: str) -> None:
    """Форма добавления события разметки; список событий лежит в session_state[key]."""
    events = st.session_state.setdefault(key, [])
    for index, event in enumerate(events):
        left, right = st.columns([6, 1])
        left.markdown(f"**{LABEL_RU[event['labelCode']]}** · «{event['quote']}» · {event['presence']} / "
                      f"{event['temporal']} / {event['subject']}")
        if right.button("Убрать", key=f"{key}-del-{index}"):
            events.pop(index)
            st.rerun()
    with st.form(f"{key}-form", clear_on_submit=True):
        columns = st.columns(5)
        label = columns[0].selectbox("Метка", LABELS, format_func=lambda code: LABEL_RU[code])
        presence = columns[1].selectbox("Наличие", PRESENCE)
        temporal = columns[2].selectbox("Время", TEMPORAL)
        subject = columns[3].selectbox("Субъект", SUBJECT)
        occurrence = columns[4].number_input("Вхождение (с 0)", min_value=0, value=0)
        quote = st.text_input("Цитата (точный фрагмент текста)")
        if st.form_submit_button("Добавить событие"):
            if find_quote(text, quote, int(occurrence)) is None:
                st.error("Такой цитаты нет в тексте (учтите регистр, пробелы и номер вхождения).")
            else:
                events.append(make_event(label, quote, presence, temporal, subject, int(occurrence)))
                st.rerun()


def page_record() -> None:
    st.header("Запись голоса")
    rows = read_jsonl(AUDIO)
    recorded = {row.get("prompt_id") for row in rows}
    prompts = [(f"read-{i:02}", "read", text, critical) for i, (text, critical) in enumerate(READ)]
    prompts += [(f"free-{i:02}", "free", text, []) for i, text in enumerate(FREE)]
    todo = [p for p in prompts if p[0] not in recorded]
    st.caption(f"Записано карточек: {len(prompts) - len(todo)} из {len(prompts)}. Допустимая длина до {MAX_SECONDS} с.")
    options = todo or prompts
    choice = st.selectbox("Карточка", options, format_func=lambda p: f"{p[0]} · {p[2][:60]}")
    prompt_id, kind, prompt, critical = choice
    st.info(prompt if kind == "read" else f"Говорите своими словами. Тема: {prompt}")
    condition = st.selectbox("Условия записи", CONDITIONS)
    speaker = st.text_input("Говорящий (псевдоним)", value="owner")
    audio = st.audio_input("Запись", key=f"audio-{prompt_id}")
    reference = st.text_area("Дословно сказанное (то, что реально прозвучало, без исправлений)",
                             value=prompt if kind == "read" else "", key=f"ref-{prompt_id}")
    critical_text = st.text_input("Критические фразы через «;» (варианты через «|»)",
                                  value="; ".join("|".join(item) for item in critical), key=f"crit-{prompt_id}")
    if st.button("Сохранить запись", type="primary", disabled=audio is None):
        if not reference.strip():
            st.error("Введите дословный текст.")
            return
        number = len(rows) + 1
        sample_id = f"rec-{speaker}-{number:03}"
        seconds = to_wav(audio.getvalue(), DATA / "audio" / f"{sample_id}.wav")
        if seconds > MAX_SECONDS:
            st.warning(f"Запись {seconds:.1f} с длиннее {MAX_SECONDS} с — GigaAM её не примет. Перезапишите короче.")
            (DATA / "audio" / f"{sample_id}.wav").unlink()
            return
        items = [[variant.strip() for variant in part.split("|") if variant.strip()]
                 for part in critical_text.split(";") if part.strip()]
        rows.append({"id": sample_id, "group_id": sample_id, "split": split_for_group(sample_id), "origin": "human",
                     "speaker_id": speaker, "condition": condition, "audio_path": f"audio/{sample_id}.wav",
                     "audio_seconds": round(seconds, 3), "reference_text": reference.strip(), "critical": items,
                     "events": [], "prompt_id": prompt_id, "review_status": "approved", "reviewer": speaker})
        write_jsonl(AUDIO, rows)
        st.success(f"Сохранено: {sample_id} ({seconds:.1f} с, {rows[-1]['split']})")
        st.rerun()


def page_text() -> None:
    st.header("Новый текст дневника")
    st.caption("Пишите так, как писали бы дневник: разговорно, с опечатками, отрицаниями, прошлым и будущим. "
               "Вымышленное содержание, без реальных данных.")
    text = st.text_area("Текст", height=140, key="new-text")
    scenario = st.selectbox("Тип случая", SCENARIOS)
    no_tags = st.checkbox("В тексте нет целевых признаков", key="no-tags")
    if text.strip() and not no_tags:
        event_form(text, "new-events")
    reviewer = st.text_input("Разметчик", value="owner")
    if st.button("Сохранить текст", type="primary", disabled=not text.strip()):
        events = [] if no_tags else st.session_state.get("new-events", [])
        if not events and not no_tags:
            st.error("Добавьте события или отметьте, что целевых признаков нет.")
            return
        rows = read_jsonl(TEXTS)
        sample_id = f"hand-{sum(r['origin'] == 'hand' for r in rows) + 1:03}"
        rows.append({"id": sample_id, "group_id": sample_id, "split": split_for_group(sample_id), "origin": "hand",
                     "scenario": scenario, "text": text.strip(), "events": events,
                     "review_status": "approved", "reviewer": reviewer})
        write_jsonl(TEXTS, rows)
        st.session_state["new-events"] = []
        st.session_state["new-text"] = ""
        st.success(f"Сохранено: {sample_id} ({rows[-1]['split']})")
        st.rerun()


def page_review() -> None:
    st.header("Проверка и утверждение эталонов")
    kind = st.radio("Что проверять", ["Тексты", "Аудио"], horizontal=True)
    path = TEXTS if kind == "Тексты" else AUDIO
    rows = read_jsonl(path)
    origins = sorted({r["origin"] for r in rows})
    origin = st.selectbox("Источник", ["все"] + origins)
    status = st.selectbox("Статус", ["draft", "approved", "все"])
    shown = [r for r in rows if (origin == "все" or r["origin"] == origin) and (status == "все" or r["review_status"] == status)]
    st.caption(f"Показано {len(shown)} из {len(rows)}")
    reviewer = st.text_input("Проверяющий", value="owner")
    if st.button(f"Утвердить все показанные ({len(shown)}) после просмотра", disabled=not shown):
        ids = {r["id"] for r in shown}
        for row in rows:
            if row["id"] in ids:
                row["review_status"], row["reviewer"] = "approved", reviewer
        write_jsonl(path, rows)
        st.rerun()
    for row in shown[:40]:
        with st.expander(f"{row['id']} · {row['split']} · {row['review_status']}"):
            if kind == "Тексты":
                st.markdown(highlight(row["text"], row["events"]), unsafe_allow_html=True)
                st.caption(f"сценарий: {row.get('scenario')} · актуальные признаки: {sorted(actual_labels(row['events']))}")
            else:
                st.audio(str(DATA / row["audio_path"]))
                row["reference_text"] = st.text_area("Дословный текст", row["reference_text"], key=f"r-{row['id']}")
                st.caption(f"{row.get('condition')} · {row.get('audio_seconds')} с · критические: {row.get('critical')}")
            columns = st.columns(3)
            if columns[0].button("Утвердить", key=f"ok-{row['id']}"):
                row["review_status"], row["reviewer"] = "approved", reviewer
                write_jsonl(path, rows)
                st.rerun()
            if columns[1].button("Вернуть в draft", key=f"draft-{row['id']}"):
                row["review_status"] = "draft"
                write_jsonl(path, rows)
                st.rerun()
            if columns[2].button("Удалить", key=f"del-{row['id']}"):
                write_jsonl(path, [r for r in rows if r["id"] != row["id"]])
                st.rerun()


def page_periods() -> None:
    st.header("Периоды для обзора: проверка пакетов фактов и утверждение")
    st.caption("Проверьте, что формулировки фактов верны, а отмеченные ★ факты действительно важны для врача. "
               "Утверждённые периоды разрешают прогон на test.")
    rows = read_jsonl(PERIODS)
    split = st.selectbox("Часть", ["все", "dev", "test"])
    status = st.selectbox("Статус", ["draft", "approved", "все"])
    shown = [r for r in rows if (split == "все" or r["split"] == split) and (status == "все" or r["review_status"] == status)]
    st.caption(f"Показано {len(shown)} из {len(rows)}")
    reviewer = st.text_input("Проверяющий", value="owner")
    if st.button(f"Утвердить все показанные ({len(shown)}) после просмотра", disabled=not shown):
        ids = {r["id"] for r in shown}
        for row in rows:
            if row["id"] in ids:
                row["review_status"], row["reviewer"] = "approved", reviewer
        write_jsonl(PERIODS, rows)
        st.rerun()
    for row in shown:
        required = set(row["required_fact_ids"])
        with st.expander(f"{row['id']} · {row['split']} · {row['scenario']} · дней {row['days']} · {row['review_status']}"):
            table = [{"id": alias(i), "тип": fact["type"], "важный": "★" if fact["factId"] in required else "",
                      "факт": ("«" + fact["value"]["quote"] + "»") if fact["type"] == "QUOTE" else sentence(fact)}
                     for i, fact in enumerate(row["facts"])]
            st.dataframe(table, use_container_width=True)
            columns = st.columns(2)
            if columns[0].button("Утвердить", key=f"pok-{row['id']}"):
                row["review_status"], row["reviewer"] = "approved", reviewer
                write_jsonl(PERIODS, rows)
                st.rerun()
            if columns[1].button("Вернуть в draft", key=f"pdraft-{row['id']}"):
                row["review_status"] = "draft"
                write_jsonl(PERIODS, rows)
                st.rerun()


def page_progress() -> None:
    st.header("Прогресс набора данных")
    texts, audio = read_jsonl(TEXTS), read_jsonl(AUDIO)
    st.subheader("Тексты")
    st.dataframe([{"origin": o, "split": s, "approved": a, "n": n} for (o, s, a), n in
                  sorted(_count(texts, ("origin", "split", "review_status")).items())])
    positives = {label: sum(label in actual_labels(r["events"]) for r in texts if r["split"] == "test") for label in LABELS}
    st.caption("Положительные тестовые примеры по меткам (цель ТЗ — не менее 15 на метку):")
    st.bar_chart(positives)
    st.subheader("Аудио")
    st.dataframe([{"origin": o, "split": s, "status": a, "n": n} for (o, s, a), n in
                  sorted(_count(audio, ("origin", "split", "review_status")).items())])


def _count(rows, keys):
    result = {}
    for row in rows:
        key = tuple(row.get(k) for k in keys)
        result[key] = result.get(key, 0) + 1
    return result


def main() -> None:
    st.set_page_config(page_title="Студия датасета", layout="wide")
    page = st.sidebar.radio("Раздел", ["Запись голоса", "Новый текст", "Проверка", "Периоды", "Прогресс"])
    {"Запись голоса": page_record, "Новый текст": page_text, "Проверка": page_review, "Периоды": page_periods,
     "Прогресс": page_progress}[page]()


main()
