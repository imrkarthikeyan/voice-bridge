package com.voicebridge.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.voicebridge.service.QuestionClassificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class QuestionClassificationServiceImpl implements QuestionClassificationService {

    private static final Pattern QUESTION_WORDS = Pattern.compile(
            "\\?|\\b(why|how|what|when|where|who|can|could|should|is|are|would|எப்படி|ஏன்|என்ன|எப்போது|யார்)\\b",
            Pattern.CASE_INSENSITIVE);
    private final RestClient.Builder restClientBuilder;

    @Value("${app.ai.base-url:http://localhost:8090}")
    private String aiBaseUrl;

    @Override
    public Classification classify(String text, String language) {
        try {
            JsonNode response = restClientBuilder.baseUrl(aiBaseUrl).build().post()
                    .uri("/v1/questions/classify")
                    .body(java.util.Map.of("text", text, "language", language == null ? "" : language))
                    .retrieve().body(JsonNode.class);
            if (response != null && response.has("type")) {
                return Classification.builder()
                        .question(response.path("isQuestion").asBoolean(true))
                        .type(response.path("type").asText("CLARIFICATION"))
                        .language(response.path("language").asText(languageOrUnd(language)))
                        .translatedText(response.path("translatedText").asText(null))
                        .confidence(response.has("confidence") ? response.get("confidence").asDouble() : null)
                        .build();
            }
        } catch (RuntimeException ignored) {
            // Local classification keeps typed and spoken questions available when AI is offline.
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String type = lower.contains("why") || lower.contains("ஏன்") ? "DESIGN_RATIONALE"
                : lower.contains("how") || lower.contains("எப்படி") ? "IMPLEMENTATION"
                : lower.contains("when") || lower.contains("where") || lower.contains("எப்போது") ? "LOGISTICS"
                : lower.contains("what") || lower.contains("என்ன") ? "CONCEPTUAL" : "CLARIFICATION";
        boolean question = QUESTION_WORDS.matcher(text).find();
        return Classification.builder().question(question).type(type)
                .language(languageOrUnd(language)).confidence(question ? 0.72 : 0.35).build();
    }

    private String languageOrUnd(String language) {
        return language == null || language.isBlank() ? "und" : language;
    }
}
