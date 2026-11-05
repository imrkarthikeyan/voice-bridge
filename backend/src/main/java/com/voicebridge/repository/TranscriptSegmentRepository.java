package com.voicebridge.repository;

import com.voicebridge.entity.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {

    List<TranscriptSegment> findByMeetingIdOrderByCreatedAtAscIdAsc(Long meetingId);

    List<TranscriptSegment> findByMeetingIdAndParticipantIdAndStartMsGreaterThanEqualAndStartMsLessThanEqualAndPartialFalseOrderByStartMsAscIdAsc(
            Long meetingId, Long participantId, Long startMs, Long endMs);
}
