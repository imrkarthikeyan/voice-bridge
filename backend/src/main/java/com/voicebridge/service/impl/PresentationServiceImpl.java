package com.voicebridge.service.impl;

import com.voicebridge.dto.request.ChangeSlideRequest;
import com.voicebridge.dto.response.PresentationResponse;
import com.voicebridge.dto.response.PresentationSessionResponse;
import com.voicebridge.entity.Meeting;
import com.voicebridge.entity.Presentation;
import com.voicebridge.entity.PresentationSession;
import com.voicebridge.entity.SlideVisit;
import com.voicebridge.entity.enums.MeetingStatus;
import com.voicebridge.exception.BusinessRuleViolationException;
import com.voicebridge.exception.ResourceNotFoundException;
import com.voicebridge.mapper.PresentationMapper;
import com.voicebridge.mapper.PresentationSessionMapper;
import com.voicebridge.repository.MeetingRepository;
import com.voicebridge.repository.PresentationRepository;
import com.voicebridge.repository.PresentationSessionRepository;
import com.voicebridge.service.PresentationService;
import com.voicebridge.service.PresentationStorageService;
import com.voicebridge.service.SlideProcessorService;
import com.voicebridge.websocket.MeetingEventPublisher;
import com.voicebridge.websocket.MeetingEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresentationServiceImpl implements PresentationService {

    private final PresentationRepository presentationRepository;
    private final PresentationSessionRepository sessionRepository;
    private final MeetingRepository meetingRepository;
    private final PresentationStorageService storageService;
    private final SlideProcessorService slideProcessorService;
    private final PresentationMapper presentationMapper;
    private final PresentationSessionMapper sessionMapper;
    private final MeetingEventPublisher eventPublisher;
    private final com.voicebridge.service.SlideAnalysisService slideAnalysisService;
    private final com.voicebridge.service.SlideEnrichmentService slideEnrichmentService;
    private final com.voicebridge.repository.SlideVisitRepository slideVisitRepository;
    private final com.voicebridge.repository.SlideRepository slideRepository;

    @Value("${app.presentation.max-file-size:52428800}")
    private long maxFileSize;

    @Override
    @Transactional
    public PresentationResponse uploadPresentation(Long organizerId, Long meetingId, MultipartFile file) {
        Meeting meeting = getOwnedMeeting(organizerId, meetingId);

        if (meeting.getStatus() == MeetingStatus.CLOSED) {
            throw new BusinessRuleViolationException("Cannot upload presentation to a closed meeting");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded presentation file cannot be empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of " + (maxFileSize / (1024 * 1024)) + "MB");
        }

        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "presentation.pptx");
        String extension = getFileExtension(originalFilename);

        if (!"pptx".equalsIgnoreCase(extension) && !"pdf".equalsIgnoreCase(extension)) {
            throw new IllegalArgumentException("Unsupported file format: ." + extension + ". Only PPTX and PDF files are supported.");
        }

        String uniqueId = UUID.randomUUID().toString();
        String storagePath = storageService.storePresentationFile(file, uniqueId);

        int totalSlides;
        try (InputStream inputStream = storageService.getPresentationInputStream(storagePath, originalFilename)) {
            totalSlides = slideProcessorService.processAndExtractSlides(inputStream, extension, storagePath);
        } catch (Exception e) {
            storageService.deletePresentationDirectory(storagePath);
            if (e instanceof BusinessRuleViolationException brve) {
                throw brve;
            }
            throw new BusinessRuleViolationException("Failed to process presentation slides: " + e.getMessage());
        }

        Presentation presentation = Presentation.builder()
                .meeting(meeting)
                .fileName(originalFilename)
                .fileType(extension.toUpperCase())
                .storagePath(storagePath)
                .totalSlides(totalSlides)
                .currentSlide(1)
                .build();

        presentation = presentationRepository.save(presentation);
        try (InputStream analysisStream = storageService.getPresentationInputStream(storagePath, originalFilename)) {
            slideAnalysisService.analyze(presentation, analysisStream, extension);
            slideEnrichmentService.enqueue(presentation, meeting.getId(), meeting.getMeetingCode());
        } catch (Exception e) {
            log.warn("Slide text analysis failed for presentation {}", presentation.getId(), e);
        }
        log.info("Presentation uploaded: id={}, meetingId={}, totalSlides={}", presentation.getId(), meetingId, totalSlides);

        return presentationMapper.toResponse(presentation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PresentationResponse> listPresentations(Long meetingId) {
        return presentationRepository.findByMeetingIdOrderByUploadedAtDesc(meetingId).stream()
                .map(presentationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PresentationResponse getPresentation(Long presentationId) {
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Presentation not found"));
        return presentationMapper.toResponse(presentation);
    }

    @Override
    @Transactional
    public void deletePresentation(Long organizerId, Long presentationId) {
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Presentation not found"));

        getOwnedMeeting(organizerId, presentation.getMeeting().getId());

        sessionRepository.findByPresentationId(presentationId).ifPresent(session -> {
            if (session.isPresenting()) {
                throw new BusinessRuleViolationException("Cannot delete a presentation that is actively being presented");
            }
            sessionRepository.delete(session);
        });

        storageService.deletePresentationDirectory(presentation.getStoragePath());
        if (slideVisitRepository != null) {
            slideVisitRepository.deleteByPresentationId(presentationId);
        }
        if (slideRepository != null) {
            slideRepository.deleteByPresentationId(presentationId);
        }
        presentationRepository.delete(presentation);
        log.info("Presentation deleted: id={}", presentationId);
    }

    @Override
    @Transactional
    public PresentationSessionResponse startPresentation(Long organizerId, Long presentationId) {
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Presentation not found"));

        Meeting meeting = getOwnedMeeting(organizerId, presentation.getMeeting().getId());

        if (meeting.getStatus() == MeetingStatus.CLOSED) {
            throw new BusinessRuleViolationException("Cannot start presentation in a closed meeting");
        }

        PresentationSession session = sessionRepository.findByMeetingId(meeting.getId())
                .orElseGet(() -> PresentationSession.builder()
                .meeting(meeting)
                .presentation(presentation)
                .currentSlide(1)
                .presenting(true)
                .build());

        session.setPresentation(presentation);
        session.setPresenting(true);
        if (session.getCurrentSlide() == null || session.getCurrentSlide() < 1 || session.getCurrentSlide() > presentation.getTotalSlides()) {
            session.setCurrentSlide(1);
        }
        session = sessionRepository.save(session);
        recordSlideVisit(meeting, presentation, session.getCurrentSlide());

        PresentationSessionResponse response = sessionMapper.toResponse(session);
        eventPublisher.publish(meeting.getMeetingCode(), MeetingEventType.PRESENTATION_STARTED, response);
        log.info("Presentation started: presentationId={}, meetingCode={}", presentationId, meeting.getMeetingCode());
        return response;
    }

    @Override
    @Transactional
    public PresentationSessionResponse stopPresentation(Long organizerId, Long presentationId) {
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Presentation not found"));

        Meeting meeting = getOwnedMeeting(organizerId, presentation.getMeeting().getId());

        PresentationSession session = sessionRepository.findByMeetingId(meeting.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("No active presentation session for this meeting"));

        session.setPresenting(false);
        if (slideVisitRepository != null) {
            slideVisitRepository.findFirstByMeetingIdAndLeftMsIsNullOrderByEnteredMsDesc(meeting.getId())
                    .ifPresent(visit -> {
                        visit.setLeftMs(elapsedMs(meeting));
                        slideVisitRepository.save(visit);
                    });
        }
        session = sessionRepository.save(session);

        PresentationSessionResponse response = sessionMapper.toResponse(session);
        eventPublisher.publish(meeting.getMeetingCode(), MeetingEventType.PRESENTATION_STOPPED, response);
        log.info("Presentation stopped: presentationId={}, meetingCode={}", presentationId, meeting.getMeetingCode());
        return response;
    }

    @Override
    @Transactional
    public PresentationSessionResponse changeSlide(Long organizerId, Long presentationId, ChangeSlideRequest request) {
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Presentation not found"));

        Meeting meeting = getOwnedMeeting(organizerId, presentation.getMeeting().getId());

        if (meeting.getStatus() == MeetingStatus.CLOSED) {
            throw new BusinessRuleViolationException("Cannot navigate slides in a closed meeting");
        }

        int requestedSlide = request.getSlideNumber();
        if (requestedSlide < 1 || requestedSlide > presentation.getTotalSlides()) {
            throw new IllegalArgumentException("Slide number " + requestedSlide + " is out of range (1-" + presentation.getTotalSlides() + ")");
        }

        PresentationSession session = sessionRepository.findByMeetingId(meeting.getId())
                .orElseGet(() -> PresentationSession.builder()
                .meeting(meeting)
                .presentation(presentation)
                .currentSlide(requestedSlide)
                .presenting(true)
                .build());

        session.setPresentation(presentation);
        session.setCurrentSlide(requestedSlide);
        session = sessionRepository.save(session);
        if (slideVisitRepository != null) {
            slideVisitRepository.findFirstByMeetingIdAndLeftMsIsNullOrderByEnteredMsDesc(meeting.getId())
                    .ifPresent(visit -> {
                        visit.setLeftMs(elapsedMs(meeting));
                        slideVisitRepository.save(visit);
                    });
            recordSlideVisit(meeting, presentation, requestedSlide);
        }

        presentation.setCurrentSlide(requestedSlide);
        presentationRepository.save(presentation);

        PresentationSessionResponse response = sessionMapper.toResponse(session);
        eventPublisher.publish(meeting.getMeetingCode(), MeetingEventType.SLIDE_CHANGED, response);
        log.info("Slide changed: presentationId={}, slide={}/{}", presentationId, requestedSlide, presentation.getTotalSlides());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PresentationSessionResponse getSession(Long presentationId) {
        PresentationSession session = sessionRepository.findByPresentationId(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Presentation session not found"));
        return sessionMapper.toResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public PresentationSessionResponse getMeetingSession(Long meetingId) {
        PresentationSession session = sessionRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("No presentation session found for this meeting"));
        return sessionMapper.toResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getSlideImage(Long presentationId, Integer slideNumber) {
        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new ResourceNotFoundException("Presentation not found"));

        if (slideNumber < 1 || slideNumber > presentation.getTotalSlides()) {
            throw new IllegalArgumentException("Slide number " + slideNumber + " is out of range (1-" + presentation.getTotalSlides() + ")");
        }

        return storageService.loadSlideImage(presentation.getStoragePath(), slideNumber);
    }

    private Meeting getOwnedMeeting(Long organizerId, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));

        if (!meeting.getOrganizer().getId().equals(organizerId)) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        return meeting;
    }

    private String getFileExtension(String filename) {
        int lastIndex = filename.lastIndexOf('.');
        if (lastIndex == -1) {
            return "";
        }
        return filename.substring(lastIndex + 1);
    }

    private void recordSlideVisit(Meeting meeting, Presentation presentation, int slide) {
        if (slideVisitRepository == null) {
            return;
        }
        slideVisitRepository.save(SlideVisit.builder().meeting(meeting).presentation(presentation)
                .slideNumber(slide).enteredMs(elapsedMs(meeting)).build());
    }

    private long elapsedMs(Meeting meeting) {
        return meeting.getStartedAt() == null ? 0 : Math.max(0,
                java.time.Duration.between(meeting.getStartedAt(), java.time.Instant.now()).toMillis());
    }
}
