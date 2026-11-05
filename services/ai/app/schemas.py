from pydantic import BaseModel, Field


class TranscriptionResponse(BaseModel):
    text: str
    language: str
    language_probability: float | None = None
    duration_seconds: float | None = None
    segments: list[dict] = Field(default_factory=list)


class TranslationRequest(BaseModel):
    text: str = Field(min_length=1, max_length=120_000)
    source_language: str = Field(min_length=2, max_length=16)
    target_language: str = Field(min_length=2, max_length=16)


class TranslationResponse(BaseModel):
    text: str
    source_language: str
    target_language: str
    provider: str


class EmbeddingRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=128)


class EmbeddingResponse(BaseModel):
    model: str
    dimensions: int
    embeddings: list[list[float]]


class SummaryRequest(BaseModel):
    text: str = Field(min_length=1, max_length=120_000)
    max_length: int = Field(default=180, ge=32, le=512)
    min_length: int = Field(default=40, ge=8, le=256)


class SummaryResponse(BaseModel):
    summary: str
    model: str


class KnowledgeIndexRequest(BaseModel):
    meeting_code: str = Field(min_length=1, max_length=32)
    source_type: str = Field(min_length=1, max_length=32)
    source_ref: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=1, max_length=500_000)


class KnowledgeSearchRequest(BaseModel):
    meeting_code: str = Field(min_length=1, max_length=32)
    query: str = Field(min_length=1, max_length=2_000)
    limit: int = Field(default=5, ge=1, le=20)


class RagAnswerRequest(KnowledgeSearchRequest):
    pass


class MeetingTextRequest(BaseModel):
    meeting_code: str = Field(min_length=1, max_length=32)
    text: str = Field(min_length=1, max_length=120_000)


class ParticipantDigestRequest(MeetingTextRequest):
    participant_name: str = Field(min_length=1, max_length=120)


class AnalysisResponse(BaseModel):
    meeting_code: str
    result: dict


class SlideEnrichmentRequest(BaseModel):
    slide_number: int = Field(ge=1)
    title: str = Field(default="", max_length=500)
    text: str = Field(default="", max_length=20_000)
    notes: str = Field(default="", max_length=20_000)
    image_base64: str = Field(default="", max_length=15_000_000)
    structure: dict = Field(default_factory=dict)


class SlideEnrichmentResponse(BaseModel):
    slide_number: int
    concepts: list[str]
    embedding: list[float] | None = None
    structure: dict
    ocr_text: str = ""
    language: str = "und"


class QuestionClassificationRequest(BaseModel):
    text: str = Field(min_length=1, max_length=6000)
    language: str = Field(default="", max_length=16)


class QuestionClassificationResponse(BaseModel):
    is_question: bool
    type: str
    language: str
    translated_text: str | None = None
    confidence: float
    provider: str
