package com.voicebridge.service;

import com.voicebridge.entity.Presentation;
import com.voicebridge.entity.Slide;
import com.voicebridge.repository.SlideRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SlideAnalysisService {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{2,}");
    private final SlideRepository slideRepository;
    private final ObjectMapper objectMapper;

    public void analyze(Presentation presentation, InputStream source, String extension) throws Exception {
        List<SlideData> slideData = "pptx".equalsIgnoreCase(extension)
                ? extractPptx(source) : extractPdf(source);
        slideRepository.deleteAll(slideRepository.findByPresentationIdOrderBySlideNumberAsc(presentation.getId()));
        for (int i = 0; i < slideData.size(); i++) {
            SlideData data = slideData.get(i);
            String text = data.text().trim();
            String title = data.title().isBlank() ? "Slide " + (i + 1) : data.title();
            Set<String> concepts = new LinkedHashSet<>();
            var matcher = WORD.matcher(text);
            while (matcher.find() && concepts.size() < 20) {
                concepts.add(matcher.group().toLowerCase(Locale.ROOT));
            }
            Map<String, Object> structure = new LinkedHashMap<>();
            structure.put("blocks", text.lines().filter(line -> !line.isBlank()).count());
            structure.put("textCharacters", text.length());
            structure.put("images", data.images());
            structure.put("tables", data.tables());
            structure.put("codeBlocks", hasCode(text) ? 1 : 0);
            structure.put("layout", data.layout());
            slideRepository.save(Slide.builder().presentation(presentation).slideNumber(i + 1)
                    .title(title.length() > 500 ? title.substring(0, 500) : title)
                    .extractedText(text).conceptsJson(toJson(concepts)).notes(data.notes())
                    .structureJson(objectMapper.writeValueAsString(structure))
                    .analysisStatus("QUEUED").hasCode(hasCode(text))
                    .hasTable(data.tables() > 0 || text.contains("\t")).language("und").build());
        }
    }

    private List<SlideData> extractPptx(InputStream source) throws Exception {
        try (XMLSlideShow show = new XMLSlideShow(source)) {
            List<SlideData> result = new ArrayList<>();
            for (XSLFSlide slide : show.getSlides()) {
                StringBuilder text = new StringBuilder();
                String title = null;
                int images = 0;
                int tables = 0;
                double titleFont = 0;
                List<Map<String, Object>> layout = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String shapeText = textShape.getText();
                        text.append(shapeText).append('\n');
                        double maxFont = 0;
                        boolean monospace = false;
                        for (var paragraph : textShape.getTextParagraphs()) {
                            for (XSLFTextRun run : paragraph.getTextRuns()) {
                                if (run.getFontSize() != null) {
                                    maxFont = Math.max(maxFont, run.getFontSize());
                                }
                                String family = run.getFontFamily();
                                monospace |= family != null && (family.toLowerCase(Locale.ROOT).contains("mono")
                                        || family.toLowerCase(Locale.ROOT).contains("courier"));
                            }
                        }
                        if (title == null || maxFont > titleFont) {
                            title = shapeText.lines().filter(line -> !line.isBlank()).findFirst().orElse(title);
                            titleFont = maxFont;
                        }
                        layout.add(Map.of("type", "text", "characters", shapeText.length(), "titleFont", maxFont,
                                "monospace", monospace, "codeLanguage", guessLanguage(shapeText)));
                    } else if (shape instanceof XSLFPictureShape) {
                        images++;
                        layout.add(Map.of("type", "image"));
                    } else if (shape instanceof XSLFTable table) {
                        tables++;
                        layout.add(Map.of("type", "table", "rows", table.getNumberOfRows()));
                    }
                }
                result.add(new SlideData(text.toString(), title == null ? "" : title, readNotes(slide), images, tables, layout));
            }
            return result;
        }
    }

    private List<SlideData> extractPdf(InputStream source) throws Exception {
        try (PDDocument document = Loader.loadPDF(source.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<SlideData> result = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                PDPage pdfPage = document.getPage(page - 1);
                int images = 0;
                for (var name : pdfPage.getResources().getXObjectNames()) {
                    if (pdfPage.getResources().getXObject(name) instanceof PDImageXObject) {
                        images++;
                    }
                }
                result.add(new SlideData(text, text.lines().filter(line -> !line.isBlank()).findFirst().orElse(""), "",
                        images, text.contains("\t") ? 1 : 0, List.of(Map.of("type", "text", "images", images))));
            }

            return result;
        }
    }

    private String readNotes(XSLFSlide slide) {
        try {
            Object notes = slide.getClass().getMethod("getNotes").invoke(slide);
            if (notes == null) {
                return "";
            }
            Object text = notes.getClass().getMethod("getText").invoke(notes);
            return text == null ? "" : text.toString();
        } catch (ReflectiveOperationException ex) {
            return "";
        }
    }

    private record SlideData(String text, String title, String notes, int images, int tables,
            List<Map<String, Object>> layout) {

    }

    private String toJson(Set<String> concepts) {
        return concepts.stream().map(value -> "\"" + value.replace("\"", "") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private boolean hasCode(String text) {
        return text.contains("{") || text.contains("=>") || text.lines().anyMatch(line
                -> line.startsWith("    ") || line.matches(".*\\b(class|def|function|const|import|SELECT|public static)\\b.*"));
    }

    private String guessLanguage(String text) {
        if (text.matches("(?s).*\\b(public\\s+class|System\\.out|import\\s+java\\.).*")) {
            return "java";
        }
        if (text.matches("(?s).*\\b(def\\s+\\w+|import\\s+\\w+|print\\().*")) {
            return "python";
        }
        if (text.matches("(?s).*\\b(const|let|function|console\\.log)\\b.*")) {
            return "javascript";
        }
        if (text.matches("(?s).*\\b(SELECT|FROM|WHERE|INSERT\\s+INTO)\\b.*")) {
            return "sql";
        }
        return "unknown";
    }
}
