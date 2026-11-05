import io
import base64
import hashlib
import math
import json
import uuid

from fastapi import FastAPI, File, HTTPException, UploadFile, WebSocket, WebSocketDisconnect

from .config import get_settings
from .schemas import (
    EmbeddingRequest,
    EmbeddingResponse,
    SummaryRequest,
    SummaryResponse,
    TranscriptionResponse,
    TranslationRequest,
    TranslationResponse,
    KnowledgeIndexRequest,
    KnowledgeSearchRequest,
    RagAnswerRequest,
    MeetingTextRequest,
    ParticipantDigestRequest,
    AnalysisResponse,
    SlideEnrichmentRequest,
    SlideEnrichmentResponse,
    QuestionClassificationRequest,
    QuestionClassificationResponse,
)
from .services import (
    embed_texts,
    summarize_text,
    transcribe_async,
    translate_text,
)
from .knowledge import answer, extract_actions, index_document, meeting_summary, search
from .analytics import (
    analyze_sentiment,
    detect_decisions,
    engagement_report,
    extract_topics,
    meeting_report,
    participant_digest,
)

app = FastAPI(title="VoiceBridge Local AI", version="1.0.0")


@app.post("/v1/questions/classify", response_model=QuestionClassificationResponse)
def classify_question(request: QuestionClassificationRequest) -> QuestionClassificationResponse:
    text = request.text.strip()
    lower = text.lower()
    question_markers = (
        "?", "why", "how", "what", "when", "where", "who", "can", "could",
        "should", "would", "எப்படி", "ஏன்", "என்ன", "எப்போது", "யார்",
    )
    is_question = any(marker in lower if marker == "?" else marker in lower.split() for marker in question_markers)
    if "why" in lower or "ஏன்" in lower:
        question_type = "DESIGN_RATIONALE"
    elif "how" in lower or "எப்படி" in lower:
        question_type = "IMPLEMENTATION"
    elif any(word in lower for word in ("when", "where", "எப்போது")):
        question_type = "LOGISTICS"
    elif any(word in lower for word in ("what", "என்ன")):
        question_type = "CONCEPTUAL"
    else:
        question_type = "CLARIFICATION"
    language = request.language.strip() or "und"
    if language == "und":
        try:
            from langdetect import detect
            language = detect(text)
        except Exception:
            language = "und"
    translated_text = None
    if language not in {"und", "en", "eng"}:
        try:
            translated_text = translate_text(TranslationRequest(
                text=text, source_language=language, target_language="en"
            )).text
        except Exception:
            translated_text = None
    return QuestionClassificationResponse(
        is_question=is_question,
        type=question_type,
        language=language,
        translated_text=translated_text,
        confidence=0.86 if is_question else 0.42,
        provider="heuristic-multilingual",
    )


@app.post("/v1/slides/enrich", response_model=SlideEnrichmentResponse)
def enrich_slide(request: SlideEnrichmentRequest) -> SlideEnrichmentResponse:
    ocr_text = ""
    if request.image_base64 and len(request.text.strip()) < 20:
        try:
            import pytesseract
            from PIL import Image
            image = Image.open(io.BytesIO(base64.b64decode(request.image_base64)))
            ocr_text = pytesseract.image_to_string(image, lang=get_settings().ocr_languages).strip()
        except Exception:
            ocr_text = ""
    source = "\n".join(part for part in (request.title, request.text, request.notes, ocr_text) if part)
    concepts: list[str]
    try:
        import yake
        extractor = yake.KeywordExtractor(lan="en", n=3, top=20, dedupLim=0.8)
        concepts = [phrase for phrase, _score in extractor.extract_keywords(source)]
    except Exception:
        words = [word.strip(".,:;()[]{}").lower() for word in source.replace("\n", " ").split()]
        concepts = list(dict.fromkeys(word for word in words if len(word) >= 4))[:20]
    embedding = None
    try:
        embedding = embed_texts([f"{request.title}\n{request.text}"]).embeddings[0]
    except Exception:
        digest = hashlib.sha256(source.encode("utf-8")).digest()
        embedding = [((digest[index % len(digest)] / 127.5) - 1.0) for index in range(32)]
        norm = math.sqrt(sum(value * value for value in embedding)) or 1.0
        embedding = [value / norm for value in embedding]
    structure = {**request.structure, "conceptCount": len(concepts), "ocrApplied": bool(ocr_text)}
    language = "und"
    if ocr_text:
        try:
            from langdetect import detect
            language = detect(ocr_text)
        except Exception:
            language = "und"
    return SlideEnrichmentResponse(
        slide_number=request.slide_number,
        concepts=concepts,
        embedding=embedding,
        structure=structure,
        ocr_text=ocr_text,
        language=language,
    )


@app.get("/health")
def health() -> dict[str, str]:
    settings = get_settings()
    return {
        "status": "ok",
        "asr_model": settings.asr_model,
        "embedding_model": settings.embedding_model,
        "summarization_model": settings.summarization_model,
        "ocr_languages": settings.ocr_languages,
    }


@app.post("/v1/transcribe", response_model=TranscriptionResponse)
async def transcribe(file: UploadFile = File(...)) -> TranscriptionResponse:
    try:
        return await transcribe_async(file.file, file.filename or "audio")
    except ValueError as exc:
        raise HTTPException(status_code=413, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=503, detail="ASR model is unavailable") from exc


@app.post("/v1/translate", response_model=TranslationResponse)
def translate(request: TranslationRequest) -> TranslationResponse:
    try:
        return translate_text(request)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@app.post("/v1/embed", response_model=EmbeddingResponse)
def embed(request: EmbeddingRequest) -> EmbeddingResponse:
    try:
        return embed_texts(request.texts)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Embedding model is unavailable") from exc


@app.post("/v1/summarize", response_model=SummaryResponse)
def summarize(request: SummaryRequest) -> SummaryResponse:
    try:
        return summarize_text(request)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Summarization model is unavailable") from exc


@app.post("/v1/knowledge/index")
def index_knowledge(request: KnowledgeIndexRequest) -> dict[str, int]:
    try:
        return {"chunks_indexed": index_document(
            request.meeting_code, request.source_type, request.source_ref, request.content
        )}
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.post("/v1/knowledge/search")
def search_knowledge(request: KnowledgeSearchRequest) -> dict[str, list[dict]]:
    return {"results": search(request.meeting_code, request.query, request.limit)}


@app.post("/v1/rag/answer")
def rag_answer(request: RagAnswerRequest) -> dict:
    return answer(request.meeting_code, request.query, request.limit)


@app.post("/v1/meeting/summary")
def summarize_meeting(request: MeetingTextRequest) -> dict:
    return meeting_summary(request.text)


@app.post("/v1/meeting/actions")
def meeting_actions(request: MeetingTextRequest) -> dict[str, list[dict]]:
    return {"actions": extract_actions(request.text)}


@app.post("/v1/meeting/topics", response_model=AnalysisResponse)
def meeting_topics(request: MeetingTextRequest) -> AnalysisResponse:
    return AnalysisResponse(
        meeting_code=request.meeting_code,
        result={"topics": extract_topics(request.text)},
    )


@app.post("/v1/meeting/sentiment", response_model=AnalysisResponse)
def meeting_sentiment(request: MeetingTextRequest) -> AnalysisResponse:
    return AnalysisResponse(
        meeting_code=request.meeting_code,
        result={"sentiment": analyze_sentiment(request.text)},
    )


@app.post("/v1/meeting/decisions", response_model=AnalysisResponse)
def meeting_decisions(request: MeetingTextRequest) -> AnalysisResponse:
    return AnalysisResponse(
        meeting_code=request.meeting_code,
        result={"decisions": detect_decisions(request.text)},
    )


@app.post("/v1/meeting/report", response_model=AnalysisResponse)
def meeting_report_endpoint(request: MeetingTextRequest) -> AnalysisResponse:
    return AnalysisResponse(
        meeting_code=request.meeting_code,
        result=meeting_report(request.text, request.meeting_code),
    )


@app.post("/v1/meeting/digest", response_model=AnalysisResponse)
def participant_digest_endpoint(request: ParticipantDigestRequest) -> AnalysisResponse:
    return AnalysisResponse(
        meeting_code=request.meeting_code,
        result=participant_digest(request.text, request.participant_name),
    )


@app.websocket("/ws/audio")
async def audio_stream(websocket: WebSocket) -> None:
    await websocket.accept()
    chunks: list[bytes] = []
    segment_id = f"ai-{uuid.uuid4()}"
    revision = 0
    target_language = "en"
    try:
        while True:
            message = await websocket.receive()
            if message.get("bytes") is not None:
                chunks.append(message["bytes"])
            if message.get("text"):
                try:
                    command = json.loads(message["text"])
                except json.JSONDecodeError:
                    command = {"final": message["text"] == "final"}
            else:
                command = {}
            target_language = command.get("target_language") or target_language
            if message.get("text") == "final" or command.get("final") is False:
                audio = io.BytesIO(b"".join(chunks))
                result = await transcribe_async(audio, "stream.wav")
                revision += 1
                payload = result.model_dump()
                payload.update({
                    "segment_id": segment_id,
                    "revision": revision,
                    "is_final": bool(command.get("final", False)),
                    "confidence": result.language_probability,
                })
                if command.get("target_language") and payload["text"]:
                    try:
                        translated = translate_text(TranslationRequest(
                            text=payload["text"],
                            source_language=result.language,
                            target_language=command["target_language"],
                        ))
                        payload["translation"] = {
                            "text": translated.text,
                            "target_language": translated.target_language,
                            "model": translated.provider,
                        }
                    except RuntimeError:
                        payload["translation"] = None
                await websocket.send_json(payload)
                chunks.clear()
                if payload["is_final"]:
                    segment_id = f"ai-{uuid.uuid4()}"
                    revision = 0
    except WebSocketDisconnect:
        return
