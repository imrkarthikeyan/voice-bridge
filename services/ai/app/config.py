from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="VOICEBRIDGE_AI_", extra="ignore")

    asr_model: str = "small"
    asr_device: str = "cpu"
    asr_compute_type: str = "int8"
    asr_language: str = ""
    vad_enabled: bool = True
    vad_threshold: float = 0.01
    translation_model_family: str = "opus-mt"
    supported_translation_languages: str = "en,ta"
    embedding_model: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    summarization_model: str = "sshleifer/distilbart-cnn-12-6"
    ocr_languages: str = "eng+tam"
    max_audio_bytes: int = 25 * 1024 * 1024
    max_text_chars: int = 120_000
    knowledge_db_path: str = "data/voicebridge-ai.sqlite3"


@lru_cache
def get_settings() -> Settings:
    return Settings()
