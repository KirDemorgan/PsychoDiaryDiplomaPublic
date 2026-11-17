from __future__ import annotations

import hmac
import io
import logging
import os
import tempfile
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import soundfile
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

LOGGER = logging.getLogger("asr")
SAMPLE_RATE = 16_000
DEFAULT_MODEL_ID = "gigaam-v3-e2e"
DEFAULT_MAX_SECONDS = 26.0
DEFAULT_MAX_BYTES = 4 * 1024 * 1024


class Recognizer(Protocol):
    def transcribe(self, path: Path) -> str:
        ...


@dataclass(frozen=True)
class Settings:
    token: str
    model_id: str = DEFAULT_MODEL_ID
    max_seconds: float = DEFAULT_MAX_SECONDS
    max_bytes: int = DEFAULT_MAX_BYTES


class GigaAmRecognizer:
    def __init__(self, model: str):
        import gigaam
        self.model = gigaam.load_model(model)

    def transcribe(self, path: Path) -> str:
        result = self.model.transcribe(str(path))
        return result if isinstance(result, str) else getattr(result, "text", str(result))


def authorize(request: Request, settings: Settings) -> None:
    expected = f"Bearer {settings.token}".encode()
    supplied = request.headers.get("authorization", "").encode()
    if not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail="unauthorized")


def check_headers(request: Request, settings: Settings) -> None:
    if request.headers.get("content-type", "").split(";")[0].strip().lower() != "audio/wav":
        raise HTTPException(status_code=415, detail="audio_wav_required")
    if request.headers.get("x-speech-model") != settings.model_id:
        raise HTTPException(status_code=400, detail="model_mismatch")
    if request.headers.get("x-speech-language", "ru") != "ru":
        raise HTTPException(status_code=400, detail="language_unsupported")


async def read_body(request: Request, limit: int) -> bytes:
    chunks, size = [], 0
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit:
            raise HTTPException(status_code=413, detail="payload_too_large")
        chunks.append(chunk)
    return b"".join(chunks)


def audio_duration(data: bytes, settings: Settings) -> float:
    try:
        info = soundfile.info(io.BytesIO(data))
    except RuntimeError:
        raise HTTPException(status_code=415, detail="invalid_wav")
    if (info.format, info.subtype, info.channels, info.samplerate) != ("WAV", "PCM_16", 1, SAMPLE_RATE):
        raise HTTPException(status_code=415, detail="wav_pcm16_mono_16khz_required")
    if info.duration > settings.max_seconds:
        raise HTTPException(status_code=413, detail="recording_too_long")
    return info.duration


def create_app(recognizer: Recognizer, settings: Settings) -> FastAPI:
    app = FastAPI(title="Diary ASR", docs_url=None, redoc_url=None, openapi_url=None)
    lock = threading.Lock()

    def recognize(data: bytes) -> str:
        with tempfile.NamedTemporaryFile(suffix=".wav") as handle:
            handle.write(data)
            handle.flush()
            with lock:
                return recognizer.transcribe(Path(handle.name)).strip()

    @app.get("/health")
    def health() -> dict:
        return {"status": "UP", "modelId": settings.model_id}

    @app.post("/transcribe")
    async def transcribe(request: Request) -> JSONResponse:
        authorize(request, settings)
        check_headers(request, settings)
        data = await read_body(request, settings.max_bytes)
        duration = audio_duration(data, settings)
        started = time.perf_counter()
        try:
            text = await run_in_threadpool(recognize, data)
        except Exception:
            LOGGER.exception("recognition failed")
            raise HTTPException(status_code=500, detail="recognition_failed")
        LOGGER.info("transcribed %.1f s audio in %.2f s", duration, time.perf_counter() - started)
        return JSONResponse({"text": text, "modelId": settings.model_id})

    return app


def create_from_env() -> FastAPI:
    token = os.environ.get("ASR_TOKEN", "")
    if not token:
        raise SystemExit("ASR_TOKEN is required")
    settings = Settings(token=token, model_id=os.environ.get("ASR_MODEL_ID", DEFAULT_MODEL_ID),
                        max_seconds=float(os.environ.get("ASR_MAX_SECONDS", DEFAULT_MAX_SECONDS)))
    return create_app(GigaAmRecognizer(os.environ.get("ASR_GIGAAM_MODEL", "v3_e2e_rnnt")), settings)
