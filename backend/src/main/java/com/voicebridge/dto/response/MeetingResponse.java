package com.voicebridge.dto.response;

import com.voicebridge.entity.enums.MeetingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingResponse {

    private Long id;
    private String meetingCode;
    private String title;
    private String description;
    private MeetingStatus status;
    private String joinUrl;
    private String qrCodeUrl;
    private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;
    private Instant closedAt;
    private Instant updatedAt;
}
