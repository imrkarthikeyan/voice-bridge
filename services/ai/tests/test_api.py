from fastapi.testclient import TestClient

from app.main import app
from app.config import get_settings


client = TestClient(app)


def test_health_reports_configured_models():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["asr_model"]


def test_translation_rejects_blank_text():
    response = client.post(
        "/v1/translate",
        json={"text": "", "source_language": "en", "target_language": "ta"},
    )
    assert response.status_code == 422


def test_knowledge_retrieval_and_rag_answer(tmp_path, monkeypatch):
    get_settings.cache_clear()
    monkeypatch.setenv("VOICEBRIDGE_AI_KNOWLEDGE_DB_PATH", str(tmp_path / "knowledge.sqlite3"))
    indexed = client.post("/v1/knowledge/index", json={
        "meeting_code": "DEMO1",
        "source_type": "SLIDE",
        "source_ref": "slide-1",
        "content": "WebRTC sends audio directly from the audience browser to the organizer browser.",
    })
    assert indexed.status_code == 200
    results = client.post("/v1/knowledge/search", json={
        "meeting_code": "DEMO1", "query": "Where does WebRTC send audio?", "limit": 5,
    })
    assert results.json()["results"][0]["source_ref"] == "slide-1"
    answer = client.post("/v1/rag/answer", json={
        "meeting_code": "DEMO1", "query": "Where does WebRTC send audio?",
    })
    assert "organizer" in answer.json()["answer"]


def test_meeting_summary_and_action_extraction():
    summary = client.post("/v1/meeting/summary", json={
        "meeting_code": "DEMO2",
        "text": "The team reviewed the deployment plan. Action item: publish the test report by Friday.",
    })
    assert summary.status_code == 200
    actions = client.post("/v1/meeting/actions", json={
        "meeting_code": "DEMO2",
        "text": "Action item: publish the test report by Friday.",
    })
    assert actions.json()["actions"][0]["status"] == "OPEN"


def test_topics_sentiment_and_decisions():
    text = (
        "We discussed WebRTC deployment and database migration. "
        "We decided to use PostgreSQL. The plan is clear and helpful."
    )
    topics = client.post("/v1/meeting/topics", json={"meeting_code": "D3", "text": text})
    assert topics.status_code == 200
    assert topics.json()["result"]["topics"][0]["topic"] in {"webrtc", "deployment", "database"}
    sentiment = client.post("/v1/meeting/sentiment", json={"meeting_code": "D3", "text": text})
    assert sentiment.json()["result"]["sentiment"]["label"] == "POSITIVE"
    decisions = client.post("/v1/meeting/decisions", json={"meeting_code": "D3", "text": text})
    assert "PostgreSQL" in decisions.json()["result"]["decisions"][0]["decision"]


def test_report_and_participant_digest():
    text = "Priya will review the deployment plan. Action item: publish the report."
    report = client.post("/v1/meeting/report", json={"meeting_code": "D4", "text": text})
    assert report.status_code == 200
    assert report.json()["result"]["engagement"]["action_signal_count"] == 2
    digest = client.post("/v1/meeting/digest", json={
        "meeting_code": "D4", "participant_name": "Priya", "text": text,
    })
    assert digest.json()["result"]["participant"] == "Priya"
    assert digest.json()["result"]["follow_ups"]
