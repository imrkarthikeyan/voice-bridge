package com.voicebridge.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MeetingContextResponse {

    private Integer currentSlide;
    private String presentationTitle;
    private String currentSlideText;
    private List<TranscriptSegmentResponse> recentTranscript;
    private List<QuestionResponse> prioritizedQuestions;
    private long openQuestionCount;
}
