package com.voicebridge.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class MeetingDocumentResponse {
    Long id;
    Long meetingId;
    String status;
    Instant generatedAt;
    Instant reviewedAt;
    Instant publishedAt;
}
