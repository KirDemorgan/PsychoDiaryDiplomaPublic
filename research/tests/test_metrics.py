import jiwer
import pytest

from study.labels import make_event
from study.llm import validate_extraction
from study.metrics_asr import evaluate_asr, normalize, phrase_preserved, word_errors
from study.metrics_nlp import evaluate_nlp
from study.rules import extract


def gold(sample_id, text, events, scenario="current"):
    return {"id": sample_id, "text": text, "events": events, "scenario": scenario}


def run(parsed, valid_first=True, seconds=1.0):
    return {"status": "ok", "parsed": parsed, "valid_first": valid_first, "valid_final": parsed is not None, "seconds": seconds}


def test_wer_matches_jiwer_on_corpus():
    pairs = [("я не пил кофе сегодня", "я пил кофе сегодня"), ("мне тревожно", "мне тревожно"), ("ёлка стоит дома", "елка стоит")]
    errors = sum(word_errors(r, h)["errors"] for r, h in pairs)
    words = sum(word_errors(r, h)["words"] for r, h in pairs)
    assert errors / words == pytest.approx(jiwer.wer([normalize(r) for r, _ in pairs], [normalize(h) for _, h in pairs]))


def test_normalize_keeps_negations_and_numbers():
    assert normalize("Мне НЕ тошнило, принял 2 таблетки!") == "мне не тошнило принял 2 таблетки"


def test_phrase_preserved_requires_contiguous_words():
    assert phrase_preserved("не тошнило", "Сегодня меня не тошнило.")
    assert not phrase_preserved("не тошнило", "Сегодня меня тошнило, не знаю")


def test_asr_empty_hypothesis_counts_all_words_as_errors():
    rows = [{"id": "a", "reference_text": "раз два три", "condition": "clean", "origin": "tts", "critical": ["два"]}]
    result = evaluate_asr(rows, {"a": {"status": "error"}})
    assert result["wer"] == 1.0
    assert result["failed"] == 1
    assert result["critical"]["rate"] == 0.0


def test_asr_silence_is_reported_separately_from_wer():
    rows = [
        {"id": "s", "reference_text": "", "condition": "clean", "origin": "tts"},
        {"id": "a", "reference_text": "раз два", "condition": "clean", "origin": "tts"},
    ]
    runs = {"s": {"status": "ok", "hypothesis": "спасибо за просмотр"}, "a": {"status": "ok", "hypothesis": "раз два"}}
    result = evaluate_asr(rows, runs)
    assert result["wer"] == 0.0
    assert result["silence"] == {"n": 1, "false_text": 1, "inserted_words": 3}


def test_nlp_negation_is_not_actual_symptom():
    rows = [gold("1", "Сегодня тошноты не было", [make_event("NAUSEA", "тошноты не было", presence="NEGATED")], "negation")]
    wrong = {"candidates": [make_event("NAUSEA", "тошноты")]}
    result = evaluate_nlp(rows, {"1": run(wrong)})
    assert result["per_label"]["NAUSEA"]["fp"] == 1
    assert result["false_actual_by_scenario"]["negation"]["rate"] == 1.0


def test_nlp_exact_match_gives_perfect_scores():
    events = [make_event("CAFFEINE", "кофе"), make_event("ANXIETY", "тревожно")]
    rows = [gold("1", "Пил кофе, тревожно", events)]
    result = evaluate_nlp(rows, {"1": run({"candidates": events})})
    assert result["micro"]["f1"] == 1.0
    assert result["macro_f1"] == 1.0
    assert result["event_level"]["f1"] == 1.0


def test_nlp_invalid_answer_counts_as_missed():
    rows = [gold("1", "Пил кофе", [make_event("CAFFEINE", "кофе")])]
    result = evaluate_nlp(rows, {"1": run(None, valid_first=False)})
    assert result["per_label"]["CAFFEINE"]["fn"] == 1
    assert result["valid_final_rate"] == 0.0


def test_validation_rejects_invented_quote():
    raw = '{"candidates":[{"labelCode":"NAUSEA","quote":"тошнит","occurrence":0,"presence":"PRESENT","temporal":"CURRENT","subject":"SELF"}]}'
    parsed, error = validate_extraction(raw, "Сегодня всё хорошо")
    assert parsed is None
    assert error == "QUOTE_NOT_FOUND"


def test_validation_rejects_duplicate_json_keys():
    parsed, error = validate_extraction('{"candidates":[],"candidates":[]}', "текст")
    assert parsed is None
    assert error == "ValueError"


def test_rules_baseline_handles_basic_negation_and_past():
    found = extract("Вчера меня тошнило. Сегодня я не пил кофе.")["candidates"]
    kinds = {(e["labelCode"], e["presence"], e["temporal"]) for e in found}
    assert ("NAUSEA", "PRESENT", "PAST") in kinds
    assert ("CAFFEINE", "NEGATED", "CURRENT") in kinds


def test_critical_item_accepts_any_variant():
    rows = [{"id": "a", "reference_text": "тревога восемь", "condition": "clean", "origin": "tts", "critical": [["восемь", "8"]]}]
    result = evaluate_asr(rows, {"a": {"status": "ok", "hypothesis": "тревога 8"}})
    assert result["critical"]["rate"] == 1.0


def test_to_wav_converts_stereo_44k_to_mono_16k(tmp_path):
    import io
    import numpy as np
    import soundfile
    from study.audio_io import to_wav
    tone = np.sin(2 * np.pi * 440 * np.arange(44100) / 44100).astype("float32")
    buffer = io.BytesIO()
    soundfile.write(buffer, np.stack([tone, tone], axis=1), 44100, format="WAV")
    target = tmp_path / "a.wav"
    seconds = to_wav(buffer.getvalue(), target)
    info = soundfile.info(str(target))
    assert (info.samplerate, info.channels, info.subtype) == (16000, 1, "PCM_16")
    assert seconds == pytest.approx(1.0, abs=0.01)


def test_report_builds_from_metrics(tmp_path, monkeypatch):
    from study import report
    from study.common import write_json
    from study.metrics_asr import evaluate_asr
    from study.metrics_nlp import evaluate_nlp

    rows = [gold("1", "Пил кофе", [make_event("CAFFEINE", "кофе")], "current")]
    rows[0]["origin"] = "hand"
    nlp = {"task": "nlp", "split": "dev", "gold_rows": 1, "gold_approved": 0,
           "models": {"rules": {"hand": evaluate_nlp(rows, {"1": run({"candidates": rows[0]["events"]})}),
                                "all": evaluate_nlp(rows, {"1": run({"candidates": rows[0]["events"]})})}}}
    audio = [{"id": "a", "reference_text": "раз два", "condition": "clean", "origin": "tts"}]
    asr = {"task": "asr", "split": "dev", "gold_rows": 1, "gold_approved": 0,
           "models": {"whisper-turbo": {"all": evaluate_asr(audio, {"a": {"status": "ok", "hypothesis": "раз два", "seconds": 1, "audio_seconds": 2}})}}}
    write_json(tmp_path / "metrics" / "nlp__dev.json", nlp)
    write_json(tmp_path / "metrics" / "asr__dev.json", asr)
    monkeypatch.setattr(report, "FIGURES", tmp_path / "figures")
    target = report.build(tmp_path)
    text = target.read_text(encoding="utf-8")
    assert "## ASR (dev)" in text and "## Извлечение тегов (dev)" in text
    assert (tmp_path / "figures" / "asr_wer_dev.png").exists()
    assert (tmp_path / "figures" / "nlp_f1_dev.png").exists()


def test_blind_export_filters_systems_and_skips_fallbacks(tmp_path, monkeypatch):
    import csv
    from study import review

    runs = {
        "template": {"p1": {"published": [{"text": "Шаблон."}], "fallback": False}},
        "m-freenq": {"p1": {"published": [{"text": "Живой текст."}], "fallback": False},
                     "p2": {"published": [{"text": "Откат."}], "fallback": True}},
        "other": {"p1": {"published": [{"text": "Лишняя система."}], "fallback": False}},
    }
    periods = [{"id": p, "days": 1, "required_fact_ids": [], "facts": []} for p in ("p1", "p2")]
    monkeypatch.setattr(review, "load_runs", lambda task, split: runs)
    monkeypatch.setattr(review, "read_jsonl", lambda path: periods)
    monkeypatch.setattr(review, "RESULTS", tmp_path)
    review.export("dev", ["template", "m-freenq"], skip_fallback=True)
    assert "## r001" in (tmp_path / "review" / "report_blind_dev.facts.md").read_text(encoding="utf-8")
    with (tmp_path / "review" / "report_blind_dev.csv").open(encoding="utf-8", newline="") as handle:
        texts = sorted(row["text"] for row in csv.DictReader(handle))
    assert texts == ["Живой текст.", "Шаблон."]


def test_missed_level_reads_new_scale_and_maps_old_binary_column():
    from study.review import missed_level

    assert missed_level({"missed_important_0_2": "1"}) == 1
    assert missed_level({"missed_important_0_2": "0", "missed_important_0_1": "1"}) == 0
    assert missed_level({"missed_important_0_1": "1"}) == 2
    assert missed_level({"missed_important_0_1": ""}) == 0


def test_kappa_matches_hand_computed_values():
    from study.review import kappa

    first = [0] * 25 + [1] * 25
    second = [0] * 20 + [1] * 5 + [0] * 10 + [1] * 15
    assert kappa(first, second, [0, 1]) == pytest.approx(0.4)
    assert kappa([1, 2, 3, 4], [1, 2, 3, 4], [1, 2, 3, 4, 5], weighted=True) == pytest.approx(1.0)
    assert kappa([1, 5], [5, 1], [1, 2, 3, 4, 5], weighted=True) < 0
    assert kappa([1, 1, 1], [1, 1, 1], [0, 1]) is None


def test_compare_reports_agreement_on_shared_texts(tmp_path, monkeypatch):
    import csv
    import json
    from study import review

    monkeypatch.setattr(review, "RESULTS", tmp_path)
    (tmp_path / "review").mkdir()
    (tmp_path / "metrics").mkdir()

    def write(name, scores):
        with (tmp_path / "review" / name).open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.DictWriter(handle, review.FIELDS)
            writer.writeheader()
            for blind, (clarity, unsupported, missed) in scores.items():
                writer.writerow({"blind_id": blind, "period_id": "p", "text": "t", "clarity_1_5": clarity,
                                 "unsupported_0_1": unsupported, "missed_important_0_2": missed, "comment": ""})

    write("report_blind_dev.csv", {"r1": (4, 0, 0), "r2": (3, 1, 1), "r3": (5, 0, 0), "r4": (2, 0, 2)})
    write("report_blind_dev.human.csv", {"r1": (4, 0, 0), "r2": (4, 1, 0), "r3": (5, 0, 0), "r5": (1, 0, 0)})
    review.compare("dev")
    result = json.loads((tmp_path / "metrics" / "report_review_agreement__dev.json").read_text(encoding="utf-8"))
    assert result["n"] == 3
    assert result["clarity"]["exact_agreement"] == pytest.approx(2 / 3)
    assert result["clarity"]["within_one"] == 1.0
    assert result["unsupported"]["exact_agreement"] == 1.0
    assert result["missed"]["exact_agreement"] == pytest.approx(2 / 3)
