package com.voicebridge.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideAnalysisResponse {

    private Long id;
    private Integer slideNumber;
    private String title;
    private String extractedText;
    private String notes;
    private List<String> concepts;
    private boolean hasCode;
    private boolean hasTable;
    private String structureJson;
    private String embedding;
    private String analysisStatus;
    private String analysisError;
    private String language;
}
