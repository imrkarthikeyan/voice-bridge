package com.voicebridge.repository;

import com.voicebridge.entity.Slide;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SlideRepository extends JpaRepository<Slide, Long> {

    void deleteByPresentationId(Long presentationId);

    List<Slide> findByPresentationIdOrderBySlideNumberAsc(Long presentationId);

    Optional<Slide> findByPresentationIdAndSlideNumber(Long presentationId, Integer slideNumber);

    List<Slide> findByPresentationMeetingIdOrderByPresentationIdAscSlideNumberAsc(Long meetingId);
}
