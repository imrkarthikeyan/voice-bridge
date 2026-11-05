package com.voicebridge.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.voicebridge.dto.response.MeetingDocumentResponse;
import com.voicebridge.entity.*;
import com.voicebridge.exception.BusinessRuleViolationException;
import com.voicebridge.exception.ResourceNotFoundException;
import com.voicebridge.repository.*;
import com.voicebridge.service.MeetingDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingDocumentServiceImpl implements MeetingDocumentService {

    private final MeetingRepository meetingRepository;
    private final MeetingDocumentRepository documentRepository;
    private final PresentationRepository presentationRepository;
    private final SlideRepository slideRepository;
    private final TranscriptSegmentRepository transcriptRepository;
    private final QuestionRepository questionRepository;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.ai.base-url:http://localhost:8090}")
    private String aiBaseUrl;

    @Value("${app.document.font-path:/usr/share/fonts/truetype/noto/NotoSansTamil-Regular.ttf}")
    private String fontPath;

    private RestClient aiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(8_000);
        return restClientBuilder.requestFactory(factory).baseUrl(aiBaseUrl).build();
    }

    @Override
    @Transactional
    public MeetingDocumentResponse generate(Long organizerId, String meetingCode) {
        Meeting meeting = ownedMeeting(organizerId, meetingCode);
        List<TranscriptSegment> transcript = transcriptRepository.findByMeetingIdOrderByCreatedAtAscIdAsc(meeting.getId())
                .stream().filter(segment -> !segment.isPartial() && !segment.getText().isBlank()).toList();
        List<Slide> slides = slideRepository.findByPresentationMeetingIdOrderByPresentationIdAscSlideNumberAsc(meeting.getId());
        List<Question> questions = questionRepository.findByMeetingIdOrderByCreatedAtAsc(meeting.getId());

        String fullTranscript = transcript.stream().map(TranscriptSegment::getText).collect(Collectors.joining(" "));
        List<SlideSummary> slideSummaries = slides.stream()
                .map(slide -> summarizeSlide(slide, transcript))
                .toList();
        String executiveSummary = summarize(
                "Create an executive summary from this meeting material. Keep every claim grounded in the supplied text:\n"
                        + fullTranscript + "\nSlides:\n"
                        + slideSummaries.stream().map(SlideSummary::content).collect(Collectors.joining("\n")));

        String html = buildHtml(meeting, slideSummaries, questions, transcript, executiveSummary);
        byte[] pdf = renderPdf(meeting, slideSummaries, questions, transcript, executiveSummary);

        MeetingDocument document = documentRepository.findByMeetingId(meeting.getId())
                .orElseGet(() -> MeetingDocument.builder().meeting(meeting).build());
        document.setStatus("DRAFT");
        document.setHtmlContent(html);
        document.setPdfContent(pdf);
        document.setReviewedAt(null);
        document.setPublishedAt(null);
        return toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingDocumentResponse get(Long organizerId, String meetingCode) {
        Meeting meeting = ownedMeeting(organizerId, meetingCode);
        return toResponse(documentRepository.findByMeetingId(meeting.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Meeting document not found")));
    }

    @Override
    @Transactional
    public MeetingDocumentResponse review(Long organizerId, String meetingCode) {
        MeetingDocument document = ownedDocument(organizerId, meetingCode);
        if (!"DRAFT".equals(document.getStatus())) {
            throw new BusinessRuleViolationException("Only draft documents can be reviewed");
        }
        document.setStatus("REVIEWED");
        document.setReviewedAt(Instant.now());
        return toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public MeetingDocumentResponse publish(Long organizerId, String meetingCode) {
        MeetingDocument document = ownedDocument(organizerId, meetingCode);
        if (!"REVIEWED".equals(document.getStatus())) {
            throw new BusinessRuleViolationException("A document must be reviewed before publishing");
        }
        document.setStatus("PUBLISHED");
        document.setPublishedAt(Instant.now());
        return toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] download(Long organizerId, String meetingCode) {
        MeetingDocument document = ownedDocument(organizerId, meetingCode);
        return document.getPdfContent();
    }

    private MeetingDocument ownedDocument(Long organizerId, String meetingCode) {
        Meeting meeting = ownedMeeting(organizerId, meetingCode);
        return documentRepository.findByMeetingId(meeting.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Meeting document not found"));
    }

    private Meeting ownedMeeting(Long organizerId, String code) {
        Meeting meeting = meetingRepository.findByMeetingCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        if (!meeting.getOrganizer().getId().equals(organizerId)) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        return meeting;
    }

    private SlideSummary summarizeSlide(Slide slide, List<TranscriptSegment> transcript) {
        String speech = transcript.stream()
                .filter(segment -> Objects.equals(segment.getSlideNumber(), slide.getSlideNumber()))
                .map(TranscriptSegment::getText)
                .collect(Collectors.joining(" "));
        String source = ("Slide " + slide.getSlideNumber() + ": " + safe(slide.getTitle()) + "\n"
                + safe(slide.getExtractedText()) + "\nTranscript: " + speech).trim();
        return new SlideSummary(slide.getSlideNumber(), safe(slide.getTitle()), summarize(source), source);
    }

    private String summarize(String text) {
        if (text.isBlank()) {
            return "No source material was recorded.";
        }
        try {
            JsonNode response = aiClient().post().uri("/v1/summarize")
                    .body(Map.of("text", text.substring(0, Math.min(text.length(), 120_000)),
                            "max_length", 180, "min_length", 20))
                    .retrieve().body(JsonNode.class);
            if (response != null && response.has("summary")) {
                return response.get("summary").asText();
            }
        } catch (RuntimeException error) {
            log.warn("AI summary unavailable; using grounded extract", error);
        }
        return text.length() > 700 ? text.substring(0, 700) + "…" : text;
    }

    private String buildHtml(Meeting meeting, List<SlideSummary> slides, List<Question> questions,
                             List<TranscriptSegment> transcript, String executiveSummary) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html><head><meta charset="utf-8"><style>
                @page { size: A4; margin: 18mm; } body { font-family: "Noto Sans", sans-serif; color:#172033; }
                h1 { color:#123c69; } h2 { color:#1d5c8f; border-bottom:1px solid #ccd8e5; padding-bottom:4px; }
                .citation { color:#53657a; font-size:10px; } .notice { background:#eef5fb; padding:10px; }
                </style></head><body>
                """);
        html.append("<h1>").append(escape(meeting.getTitle())).append("</h1>")
                .append("<p>VoiceBridge meeting document · ").append(meeting.getMeetingCode()).append("</p>")
                .append("<p class='notice'>AI-generated, reviewed by presenter. Every generated statement is grounded in a slide or transcript source.</p>")
                .append("<h2>Executive summary</h2><p>").append(escape(executiveSummary)).append("</p>");
        html.append("<h2>Slide-by-slide summary</h2>");
        for (SlideSummary slide : slides) {
            html.append("<h3>Slide ").append(slide.number()).append(": ").append(escape(slide.title())).append("</h3>")
                    .append("<p>").append(escape(slide.content())).append("</p>")
                    .append("<p class='citation'>Source: slide ").append(slide.number()).append(" and its aligned transcript.</p>");
        }
        html.append("<h2>Audience questions and answers</h2>");
        for (Question question : questions) {
            String answer = answerFor(question, transcript);
            html.append("<h3>").append(escape(question.getText())).append("</h3>")
                    .append("<p>Asker: Audience member · status: ").append(escape(question.getStatus())).append("</p>")
                    .append("<p>Answer summary: ").append(escape(answer)).append("</p>")
                    .append("<p class='citation'>Source: question timestamp ")
                    .append(question.getStartMs() == null ? "not recorded" : question.getStartMs() + " ms").append(".</p>");
        }
        html.append("<h2>Key takeaways and transcript sources</h2><p>")
                .append(escape(executiveSummary)).append("</p><p class='citation'>")
                .append(transcript.size()).append(" final transcript segments were used.</p></body></html>");
        return html.toString();
    }

    private byte[] renderPdf(Meeting meeting, List<SlideSummary> slides, List<Question> questions,
                             List<TranscriptSegment> transcript, String executiveSummary) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font tamilFont = loadTamilFont(pdf);
            var font = tamilFont != null ? tamilFont : new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var bold = tamilFont != null ? tamilFont : new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PdfWriter writer = new PdfWriter(pdf, font, bold);
            writer.heading(meeting.getTitle());
            writer.line("VoiceBridge meeting document · " + meeting.getMeetingCode());
            writer.notice("AI-generated, reviewed by presenter. Claims are tied to slide or transcript sources.");
            writer.heading("Executive summary");
            writer.paragraph(executiveSummary);
            writer.heading("Slide-by-slide summary");
            for (SlideSummary slide : slides) {
                writer.subheading("Slide " + slide.number() + ": " + slide.title());
                writer.paragraph(slide.content());
                writer.citation("Source: slide " + slide.number() + " and aligned transcript.");
            }
            writer.heading("Audience questions and answers");
            for (Question question : questions) {
                writer.subheading(question.getText());
                writer.paragraph("Asker: Audience member · status: " + safe(question.getStatus()));
                writer.paragraph("Answer summary: " + answerFor(question, transcript));
                writer.citation("Source: question timestamp " + (question.getStartMs() == null ? "not recorded" : question.getStartMs() + " ms") + ".");
            }
            writer.heading("Key takeaways");
            writer.paragraph(executiveSummary);
            writer.citation(transcript.size() + " final transcript segments used.");
            writer.close();
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new BusinessRuleViolationException("Unable to render meeting PDF");
        }
    }

    private PDType0Font loadTamilFont(PDDocument pdf) {
        try {
            Path path = Path.of(fontPath);
            return Files.exists(path) ? PDType0Font.load(pdf, path.toFile()) : null;
        } catch (IOException error) {
            log.warn("Tamil font unavailable at {}", fontPath);
            return null;
        }
    }

    private MeetingDocumentResponse toResponse(MeetingDocument document) {
        return MeetingDocumentResponse.builder().id(document.getId()).meetingId(document.getMeeting().getId())
                .status(document.getStatus()).generatedAt(document.getGeneratedAt())
                .reviewedAt(document.getReviewedAt()).publishedAt(document.getPublishedAt()).build();
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private String answerFor(Question question, List<TranscriptSegment> transcript) {
        long questionEnd = question.getEndMs() != null
                ? question.getEndMs()
                : question.getStartMs() != null ? question.getStartMs() : Long.MIN_VALUE;
        String answer = transcript.stream()
                .filter(segment -> "PRESENTER".equalsIgnoreCase(safe(segment.getSource())))
                .filter(segment -> segment.getStartMs() != null && segment.getStartMs() >= questionEnd)
                .sorted(Comparator.comparing(TranscriptSegment::getStartMs))
                .map(TranscriptSegment::getText)
                .findFirst()
                .orElse("");
        return answer.isBlank() ? "No presenter answer was captured after this question." : summarize(answer);
    }

    private static String escape(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private record SlideSummary(int number, String title, String content, String source) {}

    private static final class PdfWriter {
        private final PDDocument document;
        private final org.apache.pdfbox.pdmodel.font.PDFont regular;
        private final org.apache.pdfbox.pdmodel.font.PDFont bold;
        private PDPage page;
        private PDPageContentStream stream;
        private float y = 760;

        PdfWriter(PDDocument document, org.apache.pdfbox.pdmodel.font.PDFont regular,
                  org.apache.pdfbox.pdmodel.font.PDFont bold) throws IOException {
            this.document = document; this.regular = regular; this.bold = bold; newPage();
        }
        void heading(String text) throws IOException { text(text, bold, 18, 28); }
        void subheading(String text) throws IOException { text(text, bold, 12, 18); }
        void line(String text) throws IOException { text(text, regular, 10, 16); }
        void notice(String text) throws IOException { text(text, regular, 9, 14); }
        void citation(String text) throws IOException { text(text, regular, 8, 12); }
        void paragraph(String text) throws IOException { for (String line : wrap(text, 95)) text(line, regular, 10, 14); }
        void close() throws IOException { stream.close(); }
        private void text(String value, org.apache.pdfbox.pdmodel.font.PDFont font, int size, int gap) throws IOException {
            for (String line : wrap(value, 95)) {
                if (y < 55) newPage();
                stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(45, y); stream.showText(line); stream.endText(); y -= gap;
            }
            y -= 4;
        }
        private void newPage() throws IOException {
            if (stream != null) stream.close();
            page = new PDPage(PDRectangle.A4); document.addPage(page);
            stream = new PDPageContentStream(document, page); y = 790;
        }
        private List<String> wrap(String value, int width) {
            List<String> lines = new ArrayList<>();
            for (String paragraph : safe(value).replace('\n', ' ').split("(?<=\\.)\\s+")) {
                StringBuilder current = new StringBuilder();
                for (String word : paragraph.split("\\s+")) {
                    if (current.length() + word.length() + 1 > width && current.length() > 0) {
                        lines.add(current.toString()); current.setLength(0);
                    }
                    if (current.length() > 0) current.append(' ');
                    current.append(word);
                }
                if (current.length() > 0) lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }
    }
}
