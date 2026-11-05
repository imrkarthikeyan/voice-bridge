package com.voicebridge.dto.response;
import lombok.*; import java.time.Instant;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class AudienceAuthResponse { private String token; private String tokenType; private Instant expiresAt; private AudienceAccountResponse audience; }
