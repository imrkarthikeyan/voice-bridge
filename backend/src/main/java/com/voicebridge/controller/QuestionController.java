package com.voicebridge.controller;

import com.voicebridge.dto.request.CreateQuestionRequest;
import com.voicebridge.dto.response.QuestionResponse;
import com.voicebridge.security.OrganizerPrincipal;
import com.voicebridge.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/meetings/{meetingCode}/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService service;

    @PostMapping
    public ResponseEntity<QuestionResponse> create(@PathVariable String meetingCode, @RequestHeader("X-Participant-Token") String token, @Valid @RequestBody CreateQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(meetingCode, token, request));
    }

    @GetMapping
    public List<QuestionResponse> list(@PathVariable String meetingCode, @AuthenticationPrincipal OrganizerPrincipal organizer, @RequestHeader(value = "X-Participant-Token", required = false) String token) {
        return service.list(meetingCode, organizer == null ? null : organizer.getId(), token);
    }
}
