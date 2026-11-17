import json

from study import overnight
from study.common import load_config, write_jsonl


def test_plan_orders_cheap_then_asr_then_llm_and_filters_finalists():
    config = load_config()
    names = [job.name for job in overnight.plan(config, "dev", None)]
    assert names[:2] == ["nlp:rules", "report:template"]
    assert names.index("asr:whisper-turbo") < names.index("nlp:qwen3.5-9b")
    assert "report:qwen3.5-9b:free" in names and "report:qwen3.5-9b:v1" in names
    finalists = [job.name for job in overnight.plan(config, "test", ["qwen3.5-9b", "whisper-turbo"], ["freenq"])]
    assert finalists == ["nlp:rules", "report:template", "asr:whisper-turbo", "nlp:qwen3.5-9b", "report:qwen3.5-9b:freenq"]


def test_command_runs_study_module_in_current_python():
    job = overnight.Job("asr:x", "asr", "run_asr", ["--model", "x"], "asr", "x", "x")
    cmd = overnight.command(job, ["--limit", "3"])
    assert cmd[1:] == ["-m", "study.run_asr", "--model", "x", "--limit", "3"]


def test_execute_skips_full_run_when_smoke_fails(tmp_path, monkeypatch):
    calls = []
    monkeypatch.setattr(overnight, "run_job", lambda cmd, log: calls.append(cmd) or 1)
    monkeypatch.setattr(overnight, "read_results", lambda job, split: [])
    job = overnight.Job("nlp:y", "nlp", "run_nlp", ["--model", "y"], "llm", "y", "y")
    overnight.execute(job, "dev", 3, tmp_path)
    assert job.status == "failed_smoke" and len(calls) == 1


def test_execute_marks_done_with_errors(tmp_path, monkeypatch):
    rows = [{"status": "ok"}] + [{"status": "error"}] * 3
    monkeypatch.setattr(overnight, "run_job", lambda cmd, log: 0)
    monkeypatch.setattr(overnight, "read_results", lambda job, split: rows)
    job = overnight.Job("nlp:y", "nlp", "run_nlp", ["--model", "y"], "llm", "y", "y")
    overnight.execute(job, "dev", 3, tmp_path)
    assert job.status == "done_with_errors" and (job.rows, job.errors) == (4, 3)


def test_preflight_flags_missing_model_and_unapproved_test(monkeypatch):
    monkeypatch.setattr(overnight.requests, "get", lambda *a, **k: type("R", (), {"json": lambda self: {"models": [{"name": "qwen3.5:9b"}]}})())
    config = load_config()
    monkeypatch.setattr(overnight, "read_jsonl", lambda path: [{"split": "test", "review_status": "draft", "audio_path": "a.wav"}])
    jobs = overnight.plan(config, "test", ["qwen3.5-9b", "gemma4-e4b", "gigachat3.1-lightning"], ["freenq"])
    problems = overnight.preflight(config, jobs, "test")
    status = {job.name: job.status for job in jobs}
    assert status["nlp:qwen3.5-9b"] == "pending"
    assert status["nlp:gemma4-e4b"] == "skipped" and status["nlp:gigachat3.1-lightning"] == "skipped"
    assert any("утверждено" in p for p in problems)


def test_preflight_with_pull_keeps_missing_model_pending(monkeypatch):
    monkeypatch.setattr(overnight.requests, "get", lambda *a, **k: type("R", (), {"json": lambda self: {"models": []}})())
    config = load_config()
    jobs = overnight.plan(config, "dev", ["qwen3.5-9b"])
    overnight.preflight(config, jobs, "dev", pull=True)
    assert all(job.status == "pending" for job in jobs)
    jobs = overnight.plan(config, "dev", ["qwen3.5-9b"])
    overnight.preflight(config, jobs, "dev", pull=False)
    assert all(job.status == "skipped" and "--pull" in job.detail for job in jobs if job.kind == "llm")
    assert all(job.status == "pending" for job in jobs if job.kind == "cheap")


def test_ensure_model_pulls_missing_and_reports_failure(monkeypatch):
    config = load_config()
    tags = {m["id"]: m["tag"] for m in config["llm_models"]}
    job = overnight.Job("nlp:qwen3.5-9b", "nlp", "run_nlp", [], "llm", "qwen3.5-9b", "qwen3.5-9b")
    pulled = []
    monkeypatch.setattr(overnight, "installed_models", lambda c: set())
    monkeypatch.setattr(overnight, "pull_model", lambda c, tag: pulled.append(tag))
    assert overnight.ensure_model(job, config, tags) and pulled == ["qwen3.5:9b"]
    monkeypatch.setattr(overnight, "pull_model", lambda c, tag: "диск")
    assert not overnight.ensure_model(job, config, tags) and job.status == "failed_pull"


def test_model_is_unloaded_only_when_next_llm_job_uses_another_model(monkeypatch):
    config = load_config()
    tags = {m["id"]: m["tag"] for m in config["llm_models"]}
    unloaded = []
    monkeypatch.setattr(overnight, "unload_model", lambda c, tag: unloaded.append(tag))
    jobs = [overnight.Job("a", "nlp", "run_nlp", [], "llm", "qwen3.5-9b", "x"),
            overnight.Job("b", "report", "run_report", [], "llm", "qwen3.5-9b", "y"),
            overnight.Job("c", "nlp", "run_nlp", [], "llm", "gemma4-e4b", "z")]
    for index in range(3):
        overnight.release_model(jobs, index, config, tags)
    assert unloaded == ["qwen3.5:9b", "gemma4:e4b"]


def test_check_reports_missing_data_and_ollama(monkeypatch, tmp_path):
    from study import check
    monkeypatch.setattr(check, "DATA", tmp_path)
    names = {name: status for name, status, _ in check.check_data()}
    assert names["data/texts.jsonl"] == check.FAIL and names["WAV-файлы"] == check.OK
    write_jsonl(tmp_path / "audio.jsonl", [{"id": "a", "audio_path": "audio/a.wav"}])
    assert dict((n, s) for n, s, _ in check.check_data())["WAV-файлы"] == check.FAIL
    monkeypatch.setattr(check.overnight, "installed_models", lambda config: None)
    assert check.check_ollama(load_config())[0][1] == check.FAIL
    monkeypatch.setattr(check.overnight, "installed_models", lambda config: {"qwen3.5:9b"})
    states = {n: s for n, s, _ in check.check_ollama(load_config())}
    assert states["модель qwen3.5-9b"] == check.OK and states["модель gemma4-e4b"] == check.WARN
    assert states["модель gigachat3.1-lightning"] == check.WARN


def test_smoke_skips_cheap_v1_and_unconfigured_models(monkeypatch, tmp_path):
    from study import check
    monkeypatch.setattr(check, "RESULTS", tmp_path)
    monkeypatch.setattr(check.overnight, "installed_models", lambda config: {"qwen3.5:9b", "qwen3.5:4b", "gemma4:e4b"})
    monkeypatch.setattr(check.overnight, "run_job", lambda cmd, log: 0)
    monkeypatch.setattr(check.overnight, "read_results", lambda job, split: [{"status": "ok"}])
    monkeypatch.setattr(check.overnight, "unload_model", lambda config, tag: None)
    names = [n for n, _, _ in check.smoke(load_config(), pull=False)]
    assert "проба asr:t-one" in names and "проба nlp:qwen3.5-9b" in names and "проба report:qwen3.5-9b:free" in names
    assert not any(n.endswith(":v1") or "gigachat" in n or "rules" in n or "template" in n for n in names)


def test_test_split_requires_explicit_arms_and_keeps_baselines():
    config = load_config()
    names = [job.name for job in overnight.plan(config, "test", ["gemma4-e4b"], ["freenq"])]
    assert names[:2] == ["nlp:rules", "report:template"]
    assert [n for n in names if n.startswith("report:gemma4-e4b")] == ["report:gemma4-e4b:freenq"]
