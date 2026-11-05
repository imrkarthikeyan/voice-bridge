package com.voicebridge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateSlideAnalysisRequest(
        @NotBlank
        @Size(max = 500)
        String title,
        @Size(max = 20)
        List<@NotBlank @Size(max = 120) String> concepts
        ) {

}
