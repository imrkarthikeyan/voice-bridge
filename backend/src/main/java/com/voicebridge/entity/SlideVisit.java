package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "slide_visits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;
    @Column(name = "slide_number", nullable = false)
    private Integer slideNumber;
    @Column(name = "entered_ms", nullable = false)
    private Long enteredMs;
    @Column(name = "left_ms")
    private Long leftMs;
    @Column(name = "entered_at", nullable = false, updatable = false)
    private Instant enteredAt;

    @PrePersist
    void onCreate() {
        enteredAt = Instant.now();
    }
}
