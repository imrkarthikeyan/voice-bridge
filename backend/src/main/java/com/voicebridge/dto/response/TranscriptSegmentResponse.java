package com.voicebridge.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegmentResponse {

    private Long id;
    private String segmentId;
    private Integer revision;
    private String source;
    private Long participantId;
    private String speakerName;
    private String language;
    private String text;
    private boolean partial;
    private Long startMs;
    private Long endMs;
    private Double confidence;
    private Integer slideNumber;
    private Instant createdAt;
    private Map<String, String> translations;
}
