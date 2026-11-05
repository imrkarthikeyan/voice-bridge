package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_slide_links", uniqueConstraints = {
    @UniqueConstraint(name = "uk_question_slide_link", columnNames = {"question_id", "slide_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionSlideLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slide_id", nullable = false)
    private Slide slide;
    @Column(nullable = false)
    private Double score;
}
