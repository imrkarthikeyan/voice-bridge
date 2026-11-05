package com.voicebridge.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class QuestionAnalyticsResponse {

    private long total;
    private long open;
    private long selected;
    private long answered;
    private long dismissed;
    private long duplicates;
    private Map<String, Long> byType;
    private Map<Integer, Long> bySlide;
}
