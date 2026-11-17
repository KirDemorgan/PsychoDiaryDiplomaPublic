import io

import numpy as np
import pytest
import soundfile
from fastapi.testclient import TestClient

from app import Settings, create_app

TOKEN = "secret-token"
MODEL = "gigaam-v3-e2e"
HEADERS = {"Authorization": f"Bearer {TOKEN}", "Content-Type": "audio/wav", "X-Speech-Model": MODEL,
           "X-Speech-Language": "ru"}


class Stub:
    def __init__(self, text="  привет  ", error=None):
        self.text, self.error, self.calls = text, error, 0

    def transcribe(self, path):
        self.calls += 1
        if self.error:
            raise self.error
        return self.text


def wav(seconds=1.0, rate=16_000, channels=1, subtype="PCM_16"):
    samples = np.sin(2 * np.pi * 440 * np.arange(int(seconds * rate)) / rate).astype("float32") * 0.3
    if channels == 2:
        samples = np.stack([samples, samples], axis=1)
    buffer = io.BytesIO()
    soundfile.write(buffer, samples, rate, format="WAV", subtype=subtype)
    return buffer.getvalue()


@pytest.fixture
def stub():
    return Stub()


@pytest.fixture
def client(stub):
    return TestClient(create_app(stub, Settings(token=TOKEN, max_seconds=25, max_bytes=1_000_000)))


def post(client, body, **override):
    headers = {**HEADERS, **override}
    headers = {key: value for key, value in headers.items() if value is not None}
    return client.post("/transcribe", content=body, headers=headers)


def test_returns_exactly_text_and_model_id_with_stripped_text(client):
    response = post(client, wav())
    assert response.status_code == 200
    assert response.json() == {"text": "привет", "modelId": MODEL}


def test_health_does_not_require_token(client):
    assert client.get("/health").json() == {"status": "UP", "modelId": MODEL}


@pytest.mark.parametrize("authorization", [None, "Bearer wrong", "secret-token", "Basic secret-token"])
def test_rejects_missing_or_wrong_token_without_calling_the_model(client, stub, authorization):
    assert post(client, wav(), Authorization=authorization).status_code == 401
    assert stub.calls == 0


def test_rejects_other_model_language_and_content_type(client):
    assert post(client, wav(), **{"X-Speech-Model": "whisper-turbo"}).status_code == 400
    assert post(client, wav(), **{"X-Speech-Language": "en"}).status_code == 400
    assert post(client, wav(), **{"Content-Type": "audio/mpeg"}).status_code == 415


@pytest.mark.parametrize("body", [wav(rate=44_100), wav(channels=2), wav(subtype="PCM_24"), b"not a wav"])
def test_requires_pcm16_mono_16khz_wav(client, stub, body):
    assert post(client, body).status_code == 415
    assert stub.calls == 0


def test_rejects_recording_longer_than_limit_and_oversized_payload(client, stub):
    assert post(client, wav(seconds=26)).status_code == 413
    assert post(client, b"0" * 1_000_001).status_code == 413
    assert stub.calls == 0


def test_accepts_recording_at_limit(client):
    assert post(client, wav(seconds=25)).status_code == 200


def test_recognition_failure_hides_details():
    app = create_app(Stub(error=RuntimeError("CUDA out of memory secret")), Settings(token=TOKEN))
    response = post(TestClient(app), wav())
    assert response.status_code == 500
    assert "CUDA" not in response.text and "secret" not in response.text


def test_silence_returns_empty_text():
    app = create_app(Stub(text=""), Settings(token=TOKEN))
    assert post(TestClient(app), wav()).json() == {"text": "", "modelId": MODEL}
