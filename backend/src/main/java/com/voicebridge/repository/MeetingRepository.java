package com.voicebridge.repository;

import com.voicebridge.entity.Meeting;
import com.voicebridge.entity.enums.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    Optional<Meeting> findByMeetingCode(String meetingCode);

    @EntityGraph(attributePaths = "organizer")
    Optional<Meeting> findWithOrganizerByMeetingCode(String meetingCode);

    Optional<Meeting> findByQrToken(String qrToken);

    boolean existsByMeetingCode(String meetingCode);

    boolean existsByQrToken(String qrToken);

    List<Meeting> findByOrganizerIdOrderByCreatedAtDesc(Long organizerId);

    List<Meeting> findByOrganizerIdAndStatus(Long organizerId, MeetingStatus status);
}
