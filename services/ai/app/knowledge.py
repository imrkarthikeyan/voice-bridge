import re
import sqlite3
from collections import Counter
from pathlib import Path

from .config import get_settings


def _db_path() -> Path:
    path = Path(get_settings().knowledge_db_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def _connect() -> sqlite3.Connection:
    connection = sqlite3.connect(_db_path())
    connection.row_factory = sqlite3.Row
    connection.execute(
        """CREATE TABLE IF NOT EXISTS knowledge_chunks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            meeting_code TEXT NOT NULL,
            source_type TEXT NOT NULL,
            source_ref TEXT NOT NULL,
            content TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )"""
    )
    return connection


def _tokens(text: str) -> list[str]:
    return re.findall(r"[^\W_]{2,}", text.lower(), flags=re.UNICODE)


def _chunks(content: str, size: int = 700) -> list[str]:
    words = content.split()
    return [" ".join(words[index:index + size]) for index in range(0, len(words), size)]


def index_document(meeting_code: str, source_type: str, source_ref: str, content: str) -> int:
    if not content.strip():
        raise ValueError("Knowledge content cannot be blank")
    with _connect() as connection:
        connection.execute(
            "DELETE FROM knowledge_chunks WHERE meeting_code = ? AND source_ref = ?",
            (meeting_code, source_ref),
        )
        rows = [(meeting_code, source_type, source_ref, chunk) for chunk in _chunks(content)]
        connection.executemany(
            "INSERT INTO knowledge_chunks(meeting_code, source_type, source_ref, content) VALUES (?, ?, ?, ?)",
            rows,
        )
        return len(rows)


def search(meeting_code: str, query: str, limit: int = 5) -> list[dict]:
    query_terms = Counter(_tokens(query))
    if not query_terms:
        return []
    with _connect() as connection:
        rows = connection.execute(
            "SELECT id, source_type, source_ref, content FROM knowledge_chunks WHERE meeting_code = ?",
            (meeting_code,),
        ).fetchall()
    scored = []
    for row in rows:
        terms = Counter(_tokens(row["content"]))
        overlap = sum(min(query_terms[token], terms[token]) for token in query_terms)
        if overlap:
            score = overlap / max(1, len(query_terms))
            scored.append({
                "chunk_id": row["id"],
                "source_type": row["source_type"],
                "source_ref": row["source_ref"],
                "content": row["content"],
                "score": round(score, 4),
            })
    return sorted(scored, key=lambda item: item["score"], reverse=True)[:limit]


def answer(meeting_code: str, question: str, limit: int = 5) -> dict:
    citations = search(meeting_code, question, limit)
    if not citations:
        return {"answer": "I could not find supporting information in this meeting.", "citations": []}
    question_terms = set(_tokens(question))
    sentences = []
    for citation in citations:
        for sentence in re.split(r"(?<=[.!?])\s+", citation["content"]):
            score = len(question_terms.intersection(_tokens(sentence)))
            if score:
                sentences.append((score, sentence.strip(), citation))
    sentences.sort(key=lambda item: item[0], reverse=True)
    selected = sentences[:3] or [(1, citations[0]["content"], citations[0])]
    return {
        "answer": " ".join(item[1] for item in selected),
        "citations": [item[2] for item in selected],
    }


def extract_actions(text: str) -> list[dict]:
    actions = []
    pattern = re.compile(
        r"(?:action item|todo|to-do|follow[- ]?up|need to|will)\s*[:\-]?\s*(.+?)(?:[.!?]|$)",
        re.IGNORECASE,
    )
    for index, match in enumerate(pattern.finditer(text), start=1):
        action = match.group(1).strip()
        if action:
            actions.append({"id": index, "text": action, "status": "OPEN"})
    return actions


def meeting_summary(text: str) -> dict:
    paragraphs = [part.strip() for part in re.split(r"\n{2,}|(?<=[.!?])\s+", text) if part.strip()]
    ranked = sorted(paragraphs, key=lambda item: len(_tokens(item)), reverse=True)
    selected = ranked[:5]
    return {"summary": " ".join(selected), "source": "extractive-local", "sentences": len(selected)}
