package com.voicebridge.service.impl;

import com.voicebridge.dto.request.ReorderQueueRequest;
import com.voicebridge.dto.response.SpeakingRequestResponse;
import com.voicebridge.entity.Meeting;
import com.voicebridge.entity.Participant;
import com.voicebridge.entity.SpeakerHistory;
import com.voicebridge.entity.SpeakingRequest;
import com.voicebridge.entity.enums.MeetingStatus;
import com.voicebridge.entity.enums.ParticipantStatus;
import com.voicebridge.entity.enums.SpeakerEndReason;
import com.voicebridge.entity.enums.SpeakingRequestStatus;
import com.voicebridge.exception.BusinessRuleViolationException;
import com.voicebridge.exception.ResourceNotFoundException;
import com.voicebridge.mapper.SpeakingRequestMapper;
import com.voicebridge.repository.MeetingRepository;
import com.voicebridge.repository.ParticipantRepository;
import com.voicebridge.repository.SpeakerHistoryRepository;
import com.voicebridge.repository.SpeakingRequestRepository;
import com.voicebridge.service.SpeakingRequestService;
import com.voicebridge.service.QuestionService;
import com.voicebridge.websocket.MeetingEventPublisher;
import com.voicebridge.websocket.MeetingEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingRequestServiceImpl implements SpeakingRequestService {

    private static final List<SpeakingRequestStatus> ACTIVE_STATUSES
            = List.of(SpeakingRequestStatus.WAITING, SpeakingRequestStatus.APPROVED, SpeakingRequestStatus.SPEAKING);

    private static final List<SpeakingRequestStatus> HOLDING_FLOOR_STATUSES
            = List.of(SpeakingRequestStatus.APPROVED, SpeakingRequestStatus.SPEAKING);

    private final SpeakingRequestRepository speakingRequestRepository;
    private final ParticipantRepository participantRepository;
    private final MeetingRepository meetingRepository;
    private final SpeakerHistoryRepository speakerHistoryRepository;
    private final SpeakingRequestMapper speakingRequestMapper;
    private final MeetingEventPublisher eventPublisher;
    private final QuestionService questionService;

    @Override
    @Transactional
    public SpeakingRequestResponse raiseHand(String sessionToken) {
        Participant participant = getActiveParticipant(sessionToken);
        Meeting meeting = participant.getMeeting();

        if (meeting.getStatus() == MeetingStatus.CLOSED) {
            throw new BusinessRuleViolationException("This meeting has been closed by the organizer");
        }

        if (speakingRequestRepository.existsByParticipantIdAndStatusIn(participant.getId(), ACTIVE_STATUSES)) {
            throw new BusinessRuleViolationException("You have already raised your hand");
        }

        int nextQueueOrder = nextQueueOrder(meeting.getId());

        SpeakingRequest request = SpeakingRequest.builder()
                .meeting(meeting)
                .participant(participant)
                .status(SpeakingRequestStatus.WAITING)
                .queuePosition(nextQueueOrder)
                .build();

        request = speakingRequestRepository.save(request);
        log.info("Hand raised: meetingCode={}, participantId={}, requestId={}",
                meeting.getMeetingCode(), participant.getId(), request.getId());

        SpeakingRequestResponse response = speakingRequestMapper.toResponse(request);
        eventPublisher.publish(meeting.getMeetingCode(), MeetingEventType.HAND_RAISED, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public SpeakingRequestResponse getMyRequest(String sessionToken) {
        Participant participant = getActiveParticipant(sessionToken);
        SpeakingRequest request = speakingRequestRepository
                .findByParticipantIdAndStatusIn(participant.getId(), ACTIVE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("No active speaking request"));
        return speakingRequestMapper.toResponse(request);
    }

    @Override
    @Transactional
    public SpeakingRequestResponse startSpeaking(String sessionToken) {
        Participant participant = getActiveParticipant(sessionToken);
        SpeakingRequest request = speakingRequestRepository
                .findByParticipantIdAndStatusIn(participant.getId(), List.of(SpeakingRequestStatus.APPROVED))
                .orElseThrow(() -> new BusinessRuleViolationException("You cannot speak without organizer approval"));

        request.setStatus(SpeakingRequestStatus.SPEAKING);
        request.getParticipant().setStatus(ParticipantStatus.SPEAKING);
        request.setStartedAt(Instant.now());
        request = speakingRequestRepository.save(request);

        log.info("Speaker started: meetingCode={}, participantId={}, requestId={}",
                participant.getMeeting().getMeetingCode(), participant.getId(), request.getId());

        SpeakingRequestResponse response = speakingRequestMapper.toResponse(request);
        eventPublisher.publish(participant.getMeeting().getMeetingCode(), MeetingEventType.SPEAKER_STARTED, response);
        return response;
    }

    @Override
    @Transactional
    public SpeakingRequestResponse stopSpeaking(String sessionToken) {
        Participant participant = getActiveParticipant(sessionToken);
        SpeakingRequest request = speakingRequestRepository
                .findByParticipantIdAndStatusIn(participant.getId(), List.of(SpeakingRequestStatus.SPEAKING))
                .orElseThrow(() -> new BusinessRuleViolationException("You are not currently speaking"));

        finishSpeaking(request, SpeakerEndReason.PARTICIPANT_STOPPED);
        captureQuestion(request);
        log.info("Speaker stopped (self): meetingCode={}, participantId={}, requestId={}",
                participant.getMeeting().getMeetingCode(), participant.getId(), request.getId());

        SpeakingRequestResponse response = speakingRequestMapper.toResponse(request);
        eventPublisher.publish(participant.getMeeting().getMeetingCode(), MeetingEventType.SPEAKER_ENDED, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpeakingRequestResponse> listQueue(Long organizerId, Long meetingId) {
        getOwnedMeeting(organizerId, meetingId);
        return speakingRequestRepository.findByMeetingIdOrderByQueuePositionAsc(meetingId).stream()
                .map(speakingRequestMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public synchronized SpeakingRequestResponse approve(Long organizerId, Long meetingId, Long requestId) {
        SpeakingRequest request = speakingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Speaking request not found"));

        Meeting meeting = request.getMeeting();
        if (!meeting.getOrganizer().getId().equals(organizerId)) {
            throw new ResourceNotFoundException("Speaking request not found");
        }
        if (meetingId != null && meetingId > 0 && !meeting.getId().equals(meetingId)) {
            throw new ResourceNotFoundException("Speaking request not found");
        }

        if (request.getStatus() != SpeakingRequestStatus.WAITING) {
            throw new BusinessRuleViolationException("Only a waiting request can be approved");
        }

        speakingRequestRepository.findFirstByMeetingIdAndStatusIn(meeting.getId(), HOLDING_FLOOR_STATUSES)
                .ifPresent(existing -> {
                    throw new BusinessRuleViolationException("Another participant is already approved or speaking");
                });

        request.setStatus(SpeakingRequestStatus.APPROVED);
        request.setApprovedAt(Instant.now());
        request = speakingRequestRepository.save(request);

        log.info("Speaker approved: meetingId={}, requestId={}", meeting.getId(), requestId);
        SpeakingRequestResponse response = speakingRequestMapper.toResponse(request);
        eventPublisher.publish(meeting.getMeetingCode(), MeetingEventType.SPEAKER_APPROVED, response);
        return response;
    }

    @Override
    @Transactional
    public SpeakingRequestResponse reject(Long organizerId, Long meetingId, Long requestId) {
        SpeakingRequest request = speakingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Speaking request not found"));

        Meeting meeting = request.getMeeting();
        if (!meeting.getOrganizer().getId().equals(organizerId)) {
            throw new ResourceNotFoundException("Speaking request not found");
        }
        if (meetingId != null && meetingId > 0 && !meeting.getId().equals(meetingId)) {
            throw new ResourceNotFoundException("Speaking request not found");
        }

        if (request.getStatus() != SpeakingRequestStatus.WAITING && request.getStatus() != SpeakingRequestStatus.APPROVED) {
            throw new BusinessRuleViolationException("Only a waiting or approved request can be rejected");
        }

        request.setStatus(SpeakingRequestStatus.REJECTED);
        request.setRejectedAt(Instant.now());
        request = speakingRequestRepository.save(request);

        log.info("Speaker rejected: meetingId={}, requestId={}", meeting.getId(), requestId);
        SpeakingRequestResponse response = speakingRequestMapper.toResponse(request);
        eventPublisher.publish(meeting.getMeetingCode(), MeetingEventType.SPEAKER_REJECTED, response);
        return response;
    }

    @Override
    @Transactional
    public SpeakingRequestResponse endSpeaker(Long organizerId, Long meetingId, Long requestId) {
        SpeakingRequest request;
        if (requestId != null && requestId > 0) {
            request = speakingRequestRepository.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Speaking request not found"));
            if (!request.getMeeting().getOrganizer().getId().equals(organizerId)) {
                throw new ResourceNotFoundException("Speaking request not found");
            }
        } else {
            Meeting meeting = getOwnedMeeting(organizerId, meetingId);
            request = speakingRequestRepository.findFirstByMeetingIdAndStatusIn(meeting.getId(), List.of(SpeakingRequestStatus.SPEAKING))
                    .orElseThrow(() -> new BusinessRuleViolationException("No active speaker in this meeting"));
        }

        if (request.getStatus() != SpeakingRequestStatus.SPEAKING) {
            throw new BusinessRuleViolationException("This participant is not currently speaking");
        }

        finishSpeaking(request, SpeakerEndReason.ORGANIZER_ENDED);
        captureQuestion(request);
        log.info("Speaker ended (organizer): meetingId={}, requestId={}", request.getMeeting().getId(), request.getId());

        SpeakingRequestResponse response = speakingRequestMapper.toResponse(request);
        eventPublisher.publish(request.getMeeting().getMeetingCode(), MeetingEventType.SPEAKER_ENDED, response);
        return response;
    }

    @Override
    @Transactional
    public List<SpeakingRequestResponse> reorderQueue(Long organizerId, Long meetingId, ReorderQueueRequest request) {
        Meeting meeting = getOwnedMeeting(organizerId, meetingId);

        List<SpeakingRequest> waiting = speakingRequestRepository
                .findByMeetingIdAndStatusOrderByQueuePositionAsc(meetingId, SpeakingRequestStatus.WAITING);

        List<Long> currentIds = waiting.stream().map(SpeakingRequest::getId).sorted().toList();
        List<Long> requestedIds = request.getOrderedRequestIds().stream().sorted().toList();

        if (!currentIds.equals(requestedIds)) {
            throw new BusinessRuleViolationException(
                    "orderedRequestIds must contain exactly the currently waiting requests");
        }

        int order = 1;
        for (Long id : request.getOrderedRequestIds()) {
            SpeakingRequest match = waiting.stream()
                    .filter(r -> r.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Speaking request not found: " + id));
            match.setQueuePosition(order++);
        }

        speakingRequestRepository.saveAll(waiting);
        log.info("Queue reordered: meetingId={}", meetingId);

        List<SpeakingRequestResponse> responses = speakingRequestRepository
                .findByMeetingIdOrderByQueuePositionAsc(meetingId).stream()
                .map(speakingRequestMapper::toResponse)
                .toList();
        eventPublisher.publish(meeting.getMeetingCode(), MeetingEventType.QUEUE_UPDATED, responses);
        return responses;
    }

    private void finishSpeaking(SpeakingRequest request, SpeakerEndReason endReason) {
        Instant endedAt = Instant.now();
        request.setStatus(SpeakingRequestStatus.FINISHED);
        request.setFinishedAt(endedAt);
        request.setEndedAt(endedAt);
        request.getParticipant().setStatus(ParticipantStatus.CONNECTED);
        speakingRequestRepository.save(request);

        Instant startedAt = request.getStartedAt() != null ? request.getStartedAt() : request.getApprovedAt();
        SpeakerHistory history = SpeakerHistory.builder()
                .meeting(request.getMeeting())
                .participant(request.getParticipant())
                .startedAt(startedAt)
                .endedAt(endedAt)
                .durationSeconds(Duration.between(startedAt, endedAt).getSeconds())
                .endReason(endReason)
                .build();
        speakerHistoryRepository.save(history);
    }

    private void captureQuestion(SpeakingRequest request) {
        if (questionService != null) {
            questionService.captureSpeakerTurn(request);
        }
    }

    private int nextQueueOrder(Long meetingId) {
        List<SpeakingRequest> existing = speakingRequestRepository.findByMeetingIdOrderByQueuePositionAsc(meetingId);
        return existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getQueuePosition() + 1;
    }

    private Participant getActiveParticipant(String sessionToken) {
        Participant participant = participantRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new ResourceNotFoundException("Participant session not found"));

        if (!participant.isActive()) {
            throw new BusinessRuleViolationException("Your session has ended. Please rejoin the meeting.");
        }
        return participant;
    }

    private Meeting getOwnedMeeting(Long organizerId, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));

        if (!meeting.getOrganizer().getId().equals(organizerId)) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        return meeting;
    }

    private SpeakingRequest getRequestInMeeting(Long meetingId, Long requestId) {
        SpeakingRequest request = speakingRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Speaking request not found"));

        if (!request.getMeeting().getId().equals(meetingId)) {
            throw new ResourceNotFoundException("Speaking request not found");
        }
        return request;
    }
}
