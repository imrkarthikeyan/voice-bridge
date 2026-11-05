package com.voicebridge.repository;

import com.voicebridge.entity.Participant;
import com.voicebridge.entity.enums.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByMeetingIdAndNameIgnoreCase(Long meetingId, String name);

    Optional<Participant> findBySessionToken(String sessionToken);

    @EntityGraph(attributePaths = "meeting")
    Optional<Participant> findWithMeetingBySessionToken(String sessionToken);

    List<Participant> findByMeetingIdOrderByJoinedAtAsc(Long meetingId);

    List<Participant> findByMeetingIdAndActiveTrue(Long meetingId);

    List<Participant> findByMeetingIdAndStatus(Long meetingId, ParticipantStatus status);

    boolean existsByMeetingIdAndNameIgnoreCase(Long meetingId, String name);

    long countByMeetingIdAndActiveTrue(Long meetingId);
}
