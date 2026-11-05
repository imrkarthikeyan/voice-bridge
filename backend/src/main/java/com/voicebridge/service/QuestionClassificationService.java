package com.voicebridge.service;

import lombok.Builder;
import lombok.Value;

public interface QuestionClassificationService {

    Classification classify(String text, String language);

    @Value
    @Builder
    class Classification {

        boolean question;
        String type;
        String language;
        String translatedText;
        Double confidence;
    }
}
