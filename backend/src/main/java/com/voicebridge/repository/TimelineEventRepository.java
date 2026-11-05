package com.voicebridge.repository;

import com.voicebridge.entity.TimelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {

    List<TimelineEvent> findByMeetingIdOrderByOccurredMsAsc(Long meetingId);
}
