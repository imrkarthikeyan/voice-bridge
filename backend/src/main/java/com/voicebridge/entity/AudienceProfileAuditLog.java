package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "audience_profile_audit_logs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AudienceProfileAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "account_id") private AudienceAccount account;
    private Long participantId;
    private Long meetingId;
    @Column(nullable = false, length = 40) private String action;
    @Column(columnDefinition = "TEXT") private String details;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void create() { createdAt = Instant.now(); }
}
