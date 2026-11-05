package com.voicebridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.entity.AiJob;
import com.voicebridge.entity.Presentation;
import com.voicebridge.entity.Slide;
import com.voicebridge.repository.AiJobRepository;
import com.voicebridge.repository.SlideRepository;
import com.voicebridge.repository.PresentationRepository;
import com.voicebridge.websocket.MeetingEventPublisher;
import com.voicebridge.websocket.MeetingEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlideEnrichmentService {

    private final SlideRepository slideRepository;
    private final AiJobRepository aiJobRepository;
    private final MeetingEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final PresentationStorageService storageService;
    private final PresentationRepository presentationRepository;

    @Value("${app.ai.base-url:http://localhost:8090}")
    private String aiBaseUrl;

    @Async
    @Transactional
    public void enqueue(Presentation presentation, Long meetingId, String meetingCode) {
        Long presentationId = presentation.getId();
        List<Slide> slides = slideRepository.findByPresentationIdOrderBySlideNumberAsc(presentationId);
        AiJob job = aiJobRepository.save(AiJob.builder()
                .meeting(presentation.getMeeting()).jobType("SLIDE_ENRICH").status("QUEUED")
                .payload(write(Map.of("presentationId", presentationId, "meetingId", meetingId, "slideCount", slides.size())))
                .attemptCount(0).maxAttempts(3)
                .build());
        processJob(job, presentation);
    }

    @Scheduled(fixedDelayString = "${app.ai.retry-delay-ms:30000}")
    @Transactional
    public void retryQueuedJobs() {
        aiJobRepository.findByStatusAndNextAttemptAtLessThanEqual("QUEUED", Instant.now())
                .forEach(job -> {
                    try {
                        JsonNode payload = objectMapper.readTree(job.getPayload());
                        Long id = payload.get("presentationId").asLong();
                        presentationRepository.findById(id).ifPresent(presentation -> processJob(job, presentation));
                    } catch (Exception ex) {
                        log.error("Could not recover slide enrichment job {}", job.getId(), ex);
                        job.setStatus("FAILED");
                        job.setErrorMessage("Recovery failed");
                        job.setCompletedAt(Instant.now());
                        aiJobRepository.save(job);
                    }
                });
    }

    private void processJob(AiJob job, Presentation presentation) {
        Long presentationId = presentation.getId();
        String meetingCode = presentation.getMeeting().getMeetingCode();
        List<Slide> slides = slideRepository.findByPresentationIdOrderBySlideNumberAsc(presentationId);
        try {
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setStatus("RUNNING");
            job.setStartedAt(Instant.now());
            aiJobRepository.save(job);
            for (Slide slide : slides) {
                enrichSlide(presentation, presentationId, meetingCode, slide);
            }
            job.setStatus("COMPLETED");
            job.setCompletedAt(Instant.now());
            job.setResult(write(Map.of("slides", slides.size())));
        } catch (Exception ex) {
            log.error("Slide enrichment failed for presentation {}", presentationId, ex);
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            if (job.getAttemptCount() < job.getMaxAttempts()) {
                job.setStatus("QUEUED");
                job.setNextAttemptAt(Instant.now().plusSeconds(30L * job.getAttemptCount()));
            } else {
                job.setCompletedAt(Instant.now());
            }
            slides.forEach(slide -> {
                slide.setAnalysisStatus("FAILED");
                slide.setAnalysisError("Slide enrichment failed");
                slideRepository.save(slide);
            });
        }
        aiJobRepository.save(job);
    }

    private void enrichSlide(Presentation presentation, Long presentationId, String meetingCode, Slide slide) {
        slide.setAnalysisStatus("PROCESSING");
        slideRepository.save(slide);
        try {
            String image = Base64.getEncoder().encodeToString(
                    storageService.loadSlideImage(presentation.getStoragePath(), slide.getSlideNumber()));
            JsonNode result = restClientBuilder.baseUrl(aiBaseUrl).build().post()
                    .uri("/v1/slides/enrich")
                    .body(Map.of("slideNumber", slide.getSlideNumber(), "title", slide.getTitle(),
                            "text", slide.getExtractedText(), "notes", slide.getNotes(),
                            "imageBase64", image, "structure", read(slide.getStructureJson())))
                    .retrieve().body(JsonNode.class);
            if (result != null) {
                if (result.has("concepts")) {
                    slide.setConceptsJson(result.get("concepts").toString());
                }
                if (result.has("embedding")) {
                    slide.setEmbedding(result.get("embedding").toString());
                }
                if (result.has("structure")) {
                    slide.setStructureJson(result.get("structure").toString());
                }
                if (result.has("ocr_text") && !result.get("ocr_text").asText().isBlank()) {
                    String enrichedText = slide.getExtractedText() + "\n" + result.get("ocr_text").asText();
                    slide.setExtractedText(enrichedText.substring(0, Math.min(20000, enrichedText.length())));
                }
                if (result.has("language")) {
                    slide.setLanguage(result.get("language").asText());
                }
            }
            slide.setAnalysisStatus("READY");
            slide.setAnalysisError(null);
        } catch (Exception ex) {
            log.warn("AI enrichment unavailable for slide {} in presentation {}; keeping local analysis",
                    slide.getSlideNumber(), presentationId);
            slide.setAnalysisStatus("READY");
            slide.setAnalysisError("AI enrichment unavailable; local analysis retained");
        }
        slideRepository.save(slide);
        eventPublisher.publish(meetingCode,
                MeetingEventType.SLIDE_ANALYSIS_UPDATED, Map.of(
                        "presentationId", presentationId, "slideNumber", slide.getSlideNumber(),
                        "status", slide.getAnalysisStatus()));
    }

    private Object read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize slide job payload", ex);
        }
    }
}
