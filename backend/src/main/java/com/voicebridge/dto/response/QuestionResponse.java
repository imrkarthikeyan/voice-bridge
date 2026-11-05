package com.voicebridge.dto.response;

import lombok.*;
import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private Long id;
    private Long participantId;
    private String participantName;
    private String text;
    private String originalText;
    private String type;
    private String status;
    private Integer slideNumber;
    private String language;
    private String translatedText;
    private String source;
    private Long duplicateOfId;
    private Integer priorityScore;
    private String priorityReason;
    private Long startMs;
    private Long endMs;
    private Double questionConfidence;
    private Instant createdAt;
}
