package com.voicebridge.webrtc;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignalMessage {

    @NotNull(message = "type is required")
    private SignalType type;

    @NotNull(message = "from is required")
    private SignalRole from;

    private Long participantId;

    /**
     * SDP payload for OFFER / ANSWER messages.
     */
    private String sdp;

    /**
     * ICE candidate payload (candidate string, sdpMid, sdpMLineIndex) for
     * ICE_CANDIDATE messages.
     */
    private Object candidate;

    private String speakerName;

    private String text;

    private Boolean isFinal;

    private String segmentId;

    private Integer revision;

    private String language;

    private Long startMs;

    private Long endMs;

    private Double confidence;

    private Object translation;
}
