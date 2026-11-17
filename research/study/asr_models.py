from __future__ import annotations

from pathlib import Path
from typing import Any, Dict

GIGAAM_MAX_SECONDS = 25


class AsrAdapter:
    """Общий интерфейс: путь к WAV -> текст. Тяжёлые библиотеки импортируются лениво, только для выбранной модели."""

    def transcribe(self, path: Path) -> str:
        raise NotImplementedError

    def info(self) -> Dict[str, Any]:
        return {}


class WhisperAdapter(AsrAdapter):
    def __init__(self, model: str):
        import torch
        from transformers import pipeline
        self.torch = torch
        device = "cuda:0" if torch.cuda.is_available() else "cpu"
        dtype = torch.float16 if device.startswith("cuda") else torch.float32
        self.model = model
        self.pipe = pipeline("automatic-speech-recognition", model=model, torch_dtype=dtype, device=device,
                             chunk_length_s=30)

    def transcribe(self, path: Path) -> str:
        import soundfile
        samples, rate = soundfile.read(str(path), dtype="float32")
        result = self.pipe({"array": samples, "sampling_rate": rate},
                           generate_kwargs={"language": "russian", "task": "transcribe"})
        return result["text"].strip()

    def info(self) -> Dict[str, Any]:
        return {"model": self.model, "runtime": "transformers"}


class GigaAmAdapter(AsrAdapter):
    def __init__(self, model: str):
        import gigaam
        self.model_name = model
        self.model = gigaam.load_model(model)

    def transcribe(self, path: Path) -> str:
        import soundfile
        info = soundfile.info(str(path))
        if info.duration > GIGAAM_MAX_SECONDS:
            raise ValueError(f"GigaAM transcribe принимает до {GIGAAM_MAX_SECONDS} с, получено {info.duration:.1f}")
        result = self.model.transcribe(str(path))
        return result if isinstance(result, str) else getattr(result, "text", str(result))

    def info(self) -> Dict[str, Any]:
        return {"model": self.model_name, "runtime": "gigaam"}


class ToneAdapter(AsrAdapter):
    def __init__(self):
        from tone import StreamingCTCPipeline, read_audio
        self.read_audio = read_audio
        self.pipeline = StreamingCTCPipeline.from_hugging_face()

    def transcribe(self, path: Path) -> str:
        result = self.pipeline.forward_offline(self.read_audio(str(path)))
        if isinstance(result, str):
            return result.strip()
        return " ".join(getattr(item, "text", str(item)) for item in result).strip()

    def info(self) -> Dict[str, Any]:
        return {"model": "t-tech/T-one", "runtime": "tone"}


def create(config: Dict[str, Any]) -> AsrAdapter:
    kind = config["kind"]
    if kind == "whisper":
        return WhisperAdapter(config["model"])
    if kind == "gigaam":
        return GigaAmAdapter(config["model"])
    if kind == "tone":
        return ToneAdapter()
    raise ValueError(f"Неизвестный тип ASR: {kind}")
