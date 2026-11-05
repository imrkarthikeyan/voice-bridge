package com.voicebridge.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.dto.response.SlideAnalysisResponse;
import com.voicebridge.dto.request.UpdateSlideAnalysisRequest;
import com.voicebridge.entity.Slide;
import com.voicebridge.entity.Presentation;
import com.voicebridge.exception.InvalidCredentialsException;
import com.voicebridge.exception.ResourceNotFoundException;
import com.voicebridge.repository.SlideRepository;
import com.voicebridge.security.OrganizerPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/presentations/{presentationId}/analysis")
@RequiredArgsConstructor
public class SlideController {

    private final SlideRepository slides;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<SlideAnalysisResponse> list(@PathVariable Long presentationId) {
        return slides.findByPresentationIdOrderBySlideNumberAsc(presentationId).stream().map(this::toResponse).toList();
    }

    @PutMapping("/{slideId}")
    public SlideAnalysisResponse update(@AuthenticationPrincipal OrganizerPrincipal principal,
            @PathVariable Long presentationId, @PathVariable Long slideId,
            @Valid @RequestBody UpdateSlideAnalysisRequest request) {
        if (principal == null) {
            throw new InvalidCredentialsException("Organizer authentication required");
        }
        Slide slide = slides.findById(slideId).orElseThrow(() -> new ResourceNotFoundException("Slide not found"));
        Presentation presentation = slide.getPresentation();
        if (!presentation.getId().equals(presentationId)
                || !presentation.getMeeting().getOrganizer().getId().equals(principal.getId())) {
            throw new ResourceNotFoundException("Slide not found");
        }
        slide.setTitle(request.title().trim());
        slide.setConceptsJson(toJson(request.concepts()));
        return toResponse(slides.save(slide));
    }

    private SlideAnalysisResponse toResponse(Slide s) {
        String json = s.getConceptsJson();
        List<String> concepts;
        try {
            concepts = objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            concepts = List.of();
        }
        return SlideAnalysisResponse.builder().id(s.getId()).slideNumber(s.getSlideNumber()).title(s.getTitle())
                .extractedText(s.getExtractedText()).notes(s.getNotes()).concepts(concepts)
                .hasCode(s.isHasCode()).hasTable(s.isHasTable()).structureJson(s.getStructureJson())
                .embedding(s.getEmbedding()).analysisStatus(s.getAnalysisStatus()).analysisError(s.getAnalysisError())
                .language(s.getLanguage()).build();
    }

    private String toJson(List<String> concepts) {
        try {
            return objectMapper.writeValueAsString(concepts == null ? List.of() : concepts);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Concepts could not be serialized", ex);
        }
    }
}
