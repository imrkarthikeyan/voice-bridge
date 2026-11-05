package com.voicebridge.security;

import java.security.Principal;

public record ParticipantStompPrincipal(Long participantId, String meetingCode) implements Principal {

    @Override
    public String getName() {
        return "participant:" + participantId;
    }
}
