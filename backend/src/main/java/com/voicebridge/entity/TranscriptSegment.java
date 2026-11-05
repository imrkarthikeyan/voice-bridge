package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "transcript_segments", indexes = {
    @Index(name = "idx_transcript_segments_meeting_created", columnList = "meeting_id, created_at"),
    @Index(name = "idx_transcript_segments_segment_revision", columnList = "segment_id, revision")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "segment_id", nullable = false, length = 80)
    private String segmentId;

    @Column(nullable = false)
    private Integer revision;

    @Column(length = 20)
    private String source;

    @Column(name = "participant_id")
    private Long participantId;

    @Column(length = 16)
    private String language;

    @Column(nullable = false, length = 4000)
    private String text;

    @Column(nullable = false)
    private boolean partial;

    @Column(name = "start_ms")
    private Long startMs;

    @Column(name = "end_ms")
    private Long endMs;

    private Double confidence;

    @Column(name = "slide_number")
    private Integer slideNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
