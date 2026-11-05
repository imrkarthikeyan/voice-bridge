package com.voicebridge.dto.response;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEventResponse {

    private Long id;
    private String type;
    private Long occurredMs;
    private Integer slideNumber;
    private String title;
    private String details;
}
