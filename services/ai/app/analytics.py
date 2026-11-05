import re
from collections import Counter


STOP_WORDS = {
    "the", "and", "that", "this", "with", "from", "have", "will", "are",
    "for", "you", "your", "was", "were", "about", "into", "there", "they",
    "we", "use", "plan", "clear", "discussed", "decided",
    "என்று", "மற்றும்", "இந்த", "ஒரு", "உள்ளது",
}


def _sentences(text: str) -> list[str]:
    return [item.strip() for item in re.split(r"(?<=[.!?])\s+|\n+", text) if item.strip()]


def _words(text: str) -> list[str]:
    return re.findall(r"[^\W_]{3,}", text.lower(), flags=re.UNICODE)


def extract_topics(text: str, limit: int = 8) -> list[dict]:
    sentences = _sentences(text)
    counts = Counter(word for word in _words(text) if word not in STOP_WORDS)
    topics = []
    for word, frequency in counts.most_common(limit):
        evidence = next((sentence for sentence in sentences if word in _words(sentence)), "")
        topics.append({"topic": word, "mentions": frequency, "evidence": evidence})
    return topics


def analyze_sentiment(text: str) -> dict:
    positive = {"good", "great", "success", "clear", "helpful", "agree", "excellent", "நன்று"}
    negative = {"bad", "risk", "problem", "blocked", "fail", "confused", "concern", "பிரச்சனை"}
    words = _words(text)
    positive_hits = sum(word in positive for word in words)
    negative_hits = sum(word in negative for word in words)
    total = max(1, positive_hits + negative_hits)
    if positive_hits == negative_hits:
        label = "NEUTRAL"
    elif positive_hits > negative_hits:
        label = "POSITIVE"
    else:
        label = "CONCERNED"
    return {
        "label": label,
        "positive_signals": positive_hits,
        "concern_signals": negative_hits,
        "score": round((positive_hits - negative_hits) / total, 3),
    }


def detect_decisions(text: str) -> list[dict]:
    patterns = (
        r"\b(?:we decided|decision|agreed|approved|will use|chosen|முடிவு)\b[^.!?\n]*",
    )
    matches = []
    for pattern in patterns:
        matches.extend(re.findall(pattern, text, flags=re.IGNORECASE))
    return [
        {"id": index, "decision": value.strip(" :-"), "confidence": 0.9}
        for index, value in enumerate(dict.fromkeys(matches), start=1)
    ]


def engagement_report(text: str) -> dict:
    sentences = _sentences(text)
    words = _words(text)
    questions = sum(sentence.endswith("?") for sentence in sentences)
    action_signals = len(re.findall(r"\b(action item|todo|follow[- ]?up|will)\b", text, re.I))
    return {
        "word_count": len(words),
        "sentence_count": len(sentences),
        "question_count": questions,
        "action_signal_count": action_signals,
        "average_sentence_words": round(len(words) / max(1, len(sentences)), 2),
        "engagement_level": "HIGH" if questions + action_signals >= 4 else "MEDIUM" if questions + action_signals else "LOW",
    }


def participant_digest(text: str, participant_name: str) -> dict:
    sentences = _sentences(text)
    relevant = [
        sentence for sentence in sentences
        if participant_name.lower() in sentence.lower()
        or any(term in sentence.lower() for term in ("action item", "decision", "follow-up", "todo"))
    ]
    return {
        "participant": participant_name,
        "highlights": relevant[:8],
        "follow_ups": [
            sentence for sentence in relevant
            if re.search(r"action item|follow[- ]?up|todo|will", sentence, re.I)
        ],
    }


def meeting_report(text: str, meeting_code: str) -> dict:
    return {
        "meeting_code": meeting_code,
        "topics": extract_topics(text),
        "sentiment": analyze_sentiment(text),
        "decisions": detect_decisions(text),
        "engagement": engagement_report(text),
        "actions": [
            sentence for sentence in _sentences(text)
            if re.search(r"action item|follow[- ]?up|todo|will", sentence, re.I)
        ],
    }
