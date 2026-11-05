package com.voicebridge.service;

import com.voicebridge.dto.request.ReviewQuestionRequest;
import com.voicebridge.dto.response.MeetingContextResponse;
import com.voicebridge.dto.response.QuestionAnalyticsResponse;
import com.voicebridge.dto.response.QuestionResponse;

public interface MeetingIntelligenceService {

    MeetingContextResponse getContext(String meetingCode, Long organizerId);

    QuestionResponse reviewQuestion(String meetingCode, Long organizerId, Long questionId, ReviewQuestionRequest request);

    QuestionAnalyticsResponse getQuestionAnalytics(String meetingCode, Long organizerId);
}
