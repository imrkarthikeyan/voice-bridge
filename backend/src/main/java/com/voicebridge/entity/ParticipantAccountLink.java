package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "participant_account_links")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ParticipantAccountLink {
    @Id @Column(name = "participant_id") private Long participantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id") private AudienceAccount account;
    @Column(nullable = false, updatable = false) private Instant linkedAt;
    @PrePersist void create() { linkedAt = Instant.now(); }
}
