from app.main import app
from fastapi.testclient import TestClient


def test_slide_enrichment_returns_structure_and_concepts():
    response = TestClient(app).post(
        "/v1/slides/enrich",
        json={
            "slide_number": 5,
            "title": "Microservices Architecture",
            "text": "API Gateway routes traffic to Service Discovery and Load Balancer.",
            "structure": {"images": 1, "tables": 0},
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["slide_number"] == 5
    assert body["concepts"]
    assert body["structure"]["images"] == 1
    assert "conceptCount" in body["structure"]


def test_question_classifier_structures_multilingual_question():
    response = TestClient(app).post(
        "/v1/questions/classify",
        json={"text": "Why did you choose an API gateway?", "language": "en"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["is_question"] is True
    assert body["type"] == "DESIGN_RATIONALE"
    assert body["language"] == "en"
    assert body["confidence"] > 0.5
