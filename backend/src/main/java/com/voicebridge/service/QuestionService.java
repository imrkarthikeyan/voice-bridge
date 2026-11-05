package com.voicebridge.service;

import com.voicebridge.dto.request.CreateQuestionRequest;
import com.voicebridge.dto.response.QuestionResponse;
import java.util.List;
import java.util.Optional;
import com.voicebridge.entity.SpeakingRequest;

public interface QuestionService {

    QuestionResponse create(String meetingCode, String participantToken, CreateQuestionRequest request);

    List<QuestionResponse> list(String meetingCode, Long organizerId, String participantToken);

    Optional<QuestionResponse> captureSpeakerTurn(SpeakingRequest request);
}
