package com.voicebridge.dto.response;
import lombok.*; import java.time.Instant;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class AudienceAccountResponse { private Long id; private String email; private String displayName; private Instant createdAt; }
