package com.syallabiq.syallabiq_springboot.controller;
import com.syallabiq.syallabiq_springboot.service.GroqService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class MapController {

    @Autowired
    private GroqService groqService;

    @PostMapping("/map")
    public ResponseEntity<?> mapSyllabus(
            @RequestParam(value = "syllabus",   required = false) MultipartFile syllabusFile,
            @RequestParam(value = "topics",     required = false) String topics,
            @RequestParam(value = "courseName", required = false, defaultValue = "") String courseName,
            @RequestParam(value = "courseStream",required = false, defaultValue = "") String courseStream,
            @RequestParam(value = "careerGoal", required = false, defaultValue = "") String careerGoal
    ) {
        String syllabusText = "";

        // --- PDF upload ---
        if (syllabusFile != null && !syllabusFile.isEmpty()) {
            try (PDDocument doc = Loader.loadPDF(syllabusFile.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                syllabusText = stripper.getText(doc).trim();
            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Could not read PDF: " + e.getMessage()));
            }
        }

        // --- Typed topics ---
        if (topics != null && !topics.isBlank()) {
            syllabusText = topics.trim();
        }

        if (syllabusText.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Please upload a PDF or type your syllabus topics."));
        }

        // --- Call Groq via service ---
        try {
            String result = groqService.analyze(syllabusText, courseName, courseStream, careerGoal);
            // result is already valid JSON string from Groq
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}