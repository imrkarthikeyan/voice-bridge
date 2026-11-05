package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "timeline_events", indexes = {
    @Index(name = "idx_timeline_meeting_time", columnList = "meeting_id, occurred_ms")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(name = "occurred_ms", nullable = false)
    private Long occurredMs;
    @Column(name = "slide_number")
    private Integer slideNumber;
    @Column(nullable = false, length = 300)
    private String title;
    @Column(length = 6000)
    private String details;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
