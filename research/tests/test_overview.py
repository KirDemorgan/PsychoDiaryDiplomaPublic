import json

from study import overview
from study.common import backend_prompt
from study.facts import alias, alias_map, make_fact, make_value, model_input, sentence
from study.periods import generate

MEAN = make_fact("METRIC_MEAN", make_value("ANXIETY", "7.2", count=6), [])
COVERAGE = make_fact("COVERAGE", make_value("ANXIETY", count=30, total=42), [])
FACTS = [MEAN, COVERAGE]  # id для модели: F1 = MEAN, F2 = COVERAGE


def raw(*claims):
    return json.dumps({"claims": list(claims)}, ensure_ascii=False)


def aliases_of(facts, claim):
    """Перевод реальных factId шаблонного пункта в короткие id, как их видит модель."""
    index = {fact["factId"]: alias(i) for i, fact in enumerate(facts)}
    return [index[i] for i in claim["factIds"]]


def test_free_accepts_grounded_text_and_returns_real_fact_ids():
    ok, error = overview.validate_free(raw({"text": "Средняя тревога 7.2/10 за 6 дней.", "factIds": ["F1"]}), FACTS)
    assert error is None and ok[0]["factIds"] == [MEAN["factId"]]
    bad, error = overview.validate_free(raw({"text": "Средняя тревога 7.5/10.", "factIds": ["F1"]}), FACTS)
    assert bad is None and error == "UNSUPPORTED_NUMBER"


def test_free_rejects_unknown_fact_hash_instead_of_alias_and_extra_fields():
    assert overview.validate_free(raw({"text": "Слотов 30.", "factIds": ["f-hidden"]}), FACTS)[1] == "UNKNOWN_FACT"
    assert overview.validate_free(raw({"text": "Слотов 30.", "factIds": [MEAN["factId"]]}), FACTS)[1] == "UNKNOWN_FACT"
    assert overview.validate_free(raw({"text": "Слотов 30 из 42.", "factIds": ["F2"], "type": "COVERAGE"}), FACTS)[0] is None


def test_v1_requires_exact_wording_and_matching_type():
    good = {"type": "COVERAGE", "text": sentence(COVERAGE), "factIds": ["F2"]}
    assert overview.validate_v1(raw(good), FACTS)[1] is None
    assert overview.validate_v1(raw({**good, "text": "Заполнено 30 из 42."}), FACTS)[1] == "UNSUPPORTED_WORDING"
    assert overview.validate_v1(raw({**good, "type": "METRIC_MEAN"}), FACTS)[1] == "WRONG_TYPE"


def test_model_input_uses_short_ids_and_hides_hashes_and_source_refs():
    payload = json.loads(model_input(FACTS))
    assert [item["id"] for item in payload] == ["F1", "F2"]
    assert set(payload[0]) == {"id", "type", "value", "allowedText"}
    assert MEAN["factId"] not in model_input(FACTS)


def test_template_overview_skips_quotes():
    quote = make_fact("QUOTE", make_value("SLEEP_DEFICIT", date="2026-10-05", quote="не спал"), [])
    claims = overview.template_overview([MEAN, quote])
    assert len(claims) == 1 and claims[0]["text"] == sentence(MEAN)


def test_generated_periods_are_consistent_and_template_passes_both_validators():
    periods = generate()
    assert len(periods) == 35
    assert sum(p["split"] == "dev" for p in periods) == 10
    for period in periods:
        facts = period["facts"]
        assert len({f["factId"] for f in facts}) == len(facts)
        assert set(period["required_fact_ids"]) <= {f["factId"] for f in facts}
        for claim in overview.template_overview(facts):
            ids = aliases_of(facts, claim)
            kind = next(f["type"] for f in facts if f["factId"] == claim["factIds"][0])
            assert overview.validate_free(raw({"text": claim["text"], "factIds": ids}), facts)[1] is None, (period["id"], claim["text"])
            assert overview.validate_v1(raw({"type": kind, "text": claim["text"], "factIds": ids}), facts)[1] is None


def test_report_metrics_count_fallback_and_coverage():
    from study.metrics_report import evaluate_report
    period = generate()[1]
    runs = {period["id"]: {"status": "ok", "valid_first": False, "valid_final": False, "calls": [{"error": "UNSUPPORTED_NUMBER"}],
                           "claims": None, "published": overview.template_overview(period["facts"]), "seconds": 2.0}}
    result = evaluate_report([period], runs)
    assert result["fallback_rate"] == 1.0
    assert result["rejection_reasons"] == {"UNSUPPORTED_NUMBER": 1}
    assert result["required_coverage_published"] == 1.0
    assert result["required_coverage_valid_llm"] is None


class StubClient:
    def __init__(self, *contents):
        self.contents = list(contents)

    def chat(self, system, user, schema, history=None):
        return {"content": self.contents.pop(0), "seconds": 1.0, "prompt_tokens": 1, "output_tokens": 1, "load_seconds": 0}


def test_generate_retries_once_then_publishes_valid_free_text():
    period = generate()[1]
    facts = period["facts"]
    index = next(i for i, f in enumerate(facts) if f["type"] == "METRIC_MEAN" and f["value"]["label"] == "ANXIETY")
    number = format(float(facts[index]["value"]["value"]), "g")
    bad = raw({"text": "Средняя тревога 9/10.", "factIds": [alias(index)]})
    good = raw({"text": f"Средняя тревога {number}/10.", "factIds": [alias(index)]})
    result = overview.generate(StubClient(bad, good), "free", facts)
    assert (result["valid_first"], result["valid_final"]) == (False, True)
    assert result["calls"][0]["error"] == "UNSUPPORTED_NUMBER"
    assert result["claims"][0]["factIds"] == [facts[index]["factId"]]


def test_generate_reports_failure_after_two_invalid_answers():
    period = generate()[1]
    result = overview.generate(StubClient("not json", "{}"), "v1", period["facts"])
    assert result["valid_final"] is False and result["claims"] is None


def test_extraction_retries_and_validates_quotes():
    from study.llm import extract_with_retry
    right = '{"candidates":[{"labelCode":"NAUSEA","quote":"тошнит","occurrence":0,"presence":"PRESENT","temporal":"CURRENT","subject":"SELF"}]}'
    result = extract_with_retry(StubClient("{}", right), "Сегодня тошнит")
    assert result["valid_first"] is False and result["valid_final"] is True
    assert extract_with_retry(StubClient(right, right), "Всё хорошо")["valid_final"] is False


def test_extraction_ignores_occurrence_of_unique_quote_like_the_server():
    from study.llm import validate_extraction
    wrong_index = '{"candidates":[{"labelCode":"NAUSEA","quote":"тошнит","occurrence":3,"presence":"PRESENT","temporal":"CURRENT","subject":"SELF"}]}'
    assert validate_extraction(wrong_index, "Сегодня тошнит")[1] is None
    assert validate_extraction(wrong_index, "тошнит и тошнит")[1] == "QUOTE_NOT_FOUND"


def test_prompts_are_read_from_backend_worker():
    prompt = backend_prompt("ENTRY_PROMPT")
    assert "относительно дня записи" in prompt and "не номер кандидата" in prompt
    assert overview.PROMPT_FREE == backend_prompt("REPORT_PROMPT") and "F1, F2" in overview.PROMPT_FREE


def test_free_allows_up_to_eight_facts_per_claim():
    facts = [make_fact("COVERAGE", make_value("ANXIETY", count=30, total=42), []) for _ in range(9)]
    facts = [{**fact, "factId": f"f-{i}"} for i, fact in enumerate(facts)]
    ids = [alias(i) for i in range(9)]
    assert overview.validate_free(raw({"text": "Слотов 30 из 42.", "factIds": ids[:8]}), facts)[1] is None
    assert overview.validate_free(raw({"text": "Слотов 30 из 42.", "factIds": ids}), facts)[1] is not None


def test_slots_expand_values_and_add_referenced_facts():
    text = "Средняя тревога {F1.value} из 10 при {F1.count} днях; слотов {F2.count} из {F2.total}."
    ok, error = overview.validate_slots(raw({"text": text, "factIds": ["F1"]}), FACTS)
    assert error is None
    assert ok[0]["text"] == "Средняя тревога 7.2 из 10 при 6 днях; слотов 30 из 42."
    assert ok[0]["factIds"] == [MEAN["factId"], COVERAGE["factId"]]


def test_slots_reject_bad_placeholders_and_still_check_plain_numbers():
    check = lambda text: overview.validate_slots(raw({"text": text, "factIds": ["F1"]}), FACTS)[1]
    assert check("Тревога {F9.value}.") == "UNKNOWN_FACT"
    assert check("Слотов {F2.value}.") == "EMPTY_SLOT"
    assert check("Тревога {value}.") == "BAD_SLOT"
    assert check("Средняя тревога 7.5 из 10.") == "UNSUPPORTED_NUMBER"


class ScriptedClient:
    def __init__(self, *contents):
        self.contents, self.histories = list(contents), []

    def chat(self, system, user, schema, history=None):
        self.histories.append(history)
        return {"content": self.contents.pop(0), "seconds": 0.0}


def test_feedback_retry_sends_error_back_and_plain_free_does_not():
    bad = raw({"text": "Средняя тревога 7.5/10.", "factIds": ["F1"]})
    good = raw({"text": "Средняя тревога 7.2/10 за 6 дней.", "factIds": ["F1"]})
    with_feedback = ScriptedClient(bad, good)
    result = overview.generate(with_feedback, "freefb", FACTS)
    assert result["valid_final"] and not result["valid_first"]
    assert with_feedback.histories[0] is None
    assert with_feedback.histories[1][0] == {"role": "assistant", "content": bad}
    assert "число" in with_feedback.histories[1][1]["content"]
    plain = ScriptedClient(bad, bad)
    assert not overview.generate(plain, "free", FACTS)["valid_final"]
    assert plain.histories == [None, None]


def test_slots_accept_json_key_names_and_label_words():
    text = "Средняя {F1.label}: {F1.value}, второе значение отсутствует; слотов {F2.count} из {F2.total}."
    assert overview.validate_slots(raw({"text": text.replace(", второе значение отсутствует", ""), "factIds": ["F1"]}), FACTS)[1] is None
    ok = overview.validate_slots(raw({"text": "Сравнение {F1.referenceValue}.", "factIds": ["F1"]}), FACTS)
    assert ok[1] == "EMPTY_SLOT"
    labelled = overview.expand_slots("Показатель {F1.label}.", alias_map(FACTS))[0]
    assert labelled == "Показатель тревога."


def test_no_quote_arm_hides_quote_facts_from_the_model():
    quote = make_fact("QUOTE", make_value("SLEEP_DEFICIT", date="2026-10-05", quote="плохо спал"), [])
    facts = [MEAN, quote, COVERAGE]
    seen = []

    class Spy(ScriptedClient):
        def chat(self, system, user, schema, history=None):
            seen.append(user)
            return super().chat(system, user, schema, history)

    good = raw({"text": "Слотов 30 из 42.", "factIds": ["F2"]})
    result = overview.generate(Spy(good), "freenq", facts)
    assert result["valid_final"] and "плохо спал" not in seen[0]
    assert result["claims"][0]["factIds"] == [COVERAGE["factId"]]
