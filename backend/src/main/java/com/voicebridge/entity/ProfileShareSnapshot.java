package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "profile_share_snapshots")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProfileShareSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "meeting_id") private Meeting meeting;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "participant_id") private Participant participant;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "account_id") private AudienceAccount account;
    @Column(name = "share_mode", nullable = false, length = 20) private String shareMode;
    @Column(nullable = false, columnDefinition = "TEXT") private String payload;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void create() { createdAt = Instant.now(); }
}
