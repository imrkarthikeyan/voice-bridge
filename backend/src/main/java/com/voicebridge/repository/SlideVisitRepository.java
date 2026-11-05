package com.voicebridge.repository;

import com.voicebridge.entity.SlideVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SlideVisitRepository extends JpaRepository<SlideVisit, Long> {

    void deleteByPresentationId(Long presentationId);

    List<SlideVisit> findByMeetingIdOrderByEnteredMsAsc(Long meetingId);

    Optional<SlideVisit> findFirstByMeetingIdAndLeftMsIsNullOrderByEnteredMsDesc(Long meetingId);
}
