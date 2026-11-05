import asyncio
import io
import math
import wave
from typing import BinaryIO

from .config import get_settings
from .models import get_asr_model, get_embedding_model, get_summarizer, get_vad_model
from .schemas import (
    EmbeddingResponse,
    SummaryRequest,
    SummaryResponse,
    TranscriptionResponse,
    TranslationRequest,
    TranslationResponse,
)


def _detect_language(text: str) -> tuple[str, float | None]:
    try:
        from langdetect import LangDetectException, detect_langs
        result = detect_langs(text)
    except (LangDetectException, ValueError):
        return "und", None
    if not result:
        return "und", None
    best = result[0]
    return best.lang, float(best.prob)


def transcribe_audio(audio: BinaryIO, filename: str) -> TranscriptionResponse:
    content = audio.read(get_settings().max_audio_bytes + 1)
    if len(content) > get_settings().max_audio_bytes:
        raise ValueError("Audio file exceeds configured size limit")
    model = get_asr_model()
    settings = get_settings()
    if settings.vad_enabled:
        content = _speech_only_wav(content)
        if not content:
            return TranscriptionResponse(text="", language="und", segments=[])
    transcribe_options = {
        "beam_size": 5,
        "vad_filter": settings.vad_enabled,
    }
    if settings.asr_language:
        transcribe_options["language"] = settings.asr_language
    segments, info = model.transcribe(io.BytesIO(content), **transcribe_options)
    materialized = list(segments)
    return TranscriptionResponse(
        text=" ".join(segment.text.strip() for segment in materialized).strip(),
        language=info.language,
        language_probability=info.language_probability,
        duration_seconds=info.duration,
        segments=[
            {
                "start": segment.start,
                "end": segment.end,
                "text": segment.text.strip(),
                "confidence": max(0.0, min(1.0, math.exp(segment.avg_logprob))),
            }
            for segment in materialized
        ],
    )


def _speech_only_wav(content: bytes) -> bytes:
    try:
        import torch
        from silero_vad import get_speech_timestamps

        with wave.open(io.BytesIO(content), "rb") as source:
            sample_rate = source.getframerate()
            channels = source.getnchannels()
            raw = source.readframes(source.getnframes())
        if sample_rate != 16000 or channels != 1:
            return content
        samples = torch.frombuffer(memoryview(raw), dtype=torch.int16).clone().float() / 32768.0
        timestamps = get_speech_timestamps(samples, get_vad_model(), sampling_rate=sample_rate)
        if not timestamps:
            return b""
        selected = torch.cat([samples[item["start"]:item["end"]] for item in timestamps])
        output = io.BytesIO()
        with wave.open(output, "wb") as target:
            target.setnchannels(1)
            target.setsampwidth(2)
            target.setframerate(16000)
            target.writeframes((selected.clamp(-1, 1) * 32767).short().numpy().tobytes())
        return output.getvalue()
    except (ImportError, OSError, RuntimeError, wave.Error) as error:
        import logging
        logging.getLogger(__name__).warning("Silero VAD unavailable; using full audio chunk: %s", error)
        return content


def translate_text(request: TranslationRequest) -> TranslationResponse:
    if request.source_language.lower() == request.target_language.lower():
        return TranslationResponse(
            text=request.text,
            source_language=request.source_language,
            target_language=request.target_language,
            provider="identity",
        )
    try:
        from transformers import pipeline

        translator = pipeline(
            "translation",
            model=f"Helsinki-NLP/opus-mt-{request.source_language}-{request.target_language}",
        )
        result = translator(request.text, max_length=512)[0]["translation_text"]
    except Exception as exc:
        raise RuntimeError(
            "No translation model is available for this language pair"
        ) from exc
    return TranslationResponse(
        text=result,
        source_language=request.source_language,
        target_language=request.target_language,
        provider="huggingface-opus-mt",
    )


def embed_texts(texts: list[str]) -> EmbeddingResponse:
    settings = get_settings()
    if any(not text.strip() for text in texts):
        raise ValueError("Embedding texts cannot be blank")
    model = get_embedding_model()
    vectors = model.encode(texts, normalize_embeddings=True).tolist()
    dimensions = len(vectors[0])
    return EmbeddingResponse(
        model=settings.embedding_model,
        dimensions=dimensions,
        embeddings=vectors,
    )


def summarize_text(request: SummaryRequest) -> SummaryResponse:
    settings = get_settings()
    if len(request.text) > settings.max_text_chars:
        raise ValueError("Summary input exceeds configured character limit")
    summarizer = get_summarizer()
    result = summarizer(
        request.text,
        max_length=request.max_length,
        min_length=min(request.min_length, request.max_length - 1),
        do_sample=False,
    )[0]["summary_text"].strip()
    return SummaryResponse(summary=result, model=settings.summarization_model)


async def transcribe_async(audio: BinaryIO, filename: str) -> TranscriptionResponse:
    return await asyncio.to_thread(transcribe_audio, audio, filename)
