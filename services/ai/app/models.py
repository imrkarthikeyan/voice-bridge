from functools import lru_cache
from typing import Any

from .config import get_settings


@lru_cache
def get_asr_model() -> Any:
    from faster_whisper import WhisperModel

    settings = get_settings()
    return WhisperModel(
        settings.asr_model,
        device=settings.asr_device,
        compute_type=settings.asr_compute_type,
    )


@lru_cache
def get_vad_model() -> Any:
    from silero_vad import load_silero_vad

    return load_silero_vad()


@lru_cache
def get_embedding_model() -> Any:
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(get_settings().embedding_model)


@lru_cache
def get_summarizer() -> Any:
    from transformers import pipeline

    return pipeline("summarization", model=get_settings().summarization_model)
