package com.syallabiq.syallabiq_springboot.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    = "llama-3.3-70b-versatile";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper   = new ObjectMapper();

    public String analyze(String syllabusText,
                          String courseName,
                          String courseStream,
                          String careerGoal) throws Exception {

        // Build optional context block
        List<String> contextLines = new ArrayList<>();
        if (!courseName.isBlank())   contextLines.add("Course Name: " + courseName);
        if (!courseStream.isBlank()) contextLines.add("Stream / Domain: " + courseStream);
        if (!careerGoal.isBlank())   contextLines.add("Student's Career Goal: " + careerGoal);

        String contextBlock = contextLines.isEmpty()
                ? ""
                : "STUDENT CONTEXT:\n" + String.join("\n", contextLines);

        // Truncate syllabus to ~6000 chars (same as Python version)
        String truncated = syllabusText.length() > 6000
                ? syllabusText.substring(0, 6000)
                : syllabusText;

        String prompt = """
You are an expert academic-to-industry skills mapper. Analyze the provided syllabus and map it
to practical real-world skills, project ideas, certifications, and career paths.

Be specific to the actual content. Avoid generic responses.
For certifications, you MUST provide a real, working URL to the official course or certification page.

%s

SYLLABUS CONTENT:
%s

Return ONLY valid JSON — no markdown, no explanation, no extra text:
{
  "skills": [
    {
      "skill": "Specific skill name",
      "domain": "Industry domain (e.g. Backend Development, Financial Modeling, Circuit Analysis)",
      "level": "Beginner | Intermediate | Advanced"
    }
  ],
  "project_ideas": [
    {
      "title": "Project title",
      "description": "1-2 sentences: what the student builds and what it demonstrates",
      "difficulty": "Beginner | Intermediate | Advanced",
      "skills_used": ["skill1", "skill2", "skill3"]
    }
  ],
  "certifications": [
    {
      "name": "Full certification name",
      "provider": "Issuing organization (e.g. Google, AWS, Microsoft, Coursera, Udemy)",
      "description": "One sentence on what it validates",
      "relevance": "Short phrase: why this cert aligns with the syllabus",
      "url": "Real direct URL e.g. https://grow.google/certificates/"
    }
  ],
  "career_paths": [
    {
      "role": "Job title",
      "description": "1-2 sentences: day-to-day responsibilities",
      "demand": "High | Medium | Low",
      "salary_range": "e.g. $65k-$110k / Rs. 6-16 LPA",
      "key_skills": ["skill1", "skill2", "skill3"]
    }
  ],
  "overall_summary": "3-4 sentences: the syllabus's industry relevance, what roles it prepares students for, and the top 1-2 action items the student should prioritize."
}

Constraints:
- Return 6-12 skills
- Return 4-6 project ideas (beginner to advanced range)
- Return 3-5 certifications with REAL urls
- Return 3-5 career paths
- Be specific to the actual syllabus content
""".formatted(contextBlock, truncated);

        // Build Groq request body
        String requestBody = mapper.writeValueAsString(Map.of(
                "model",       MODEL,
                "temperature", 0.3,
                "max_tokens",  4000,
                "messages",    List.of(Map.of("role", "user", "content", prompt))
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API error: " + response.statusCode() + " — " + response.body());
        }

        // Extract the content string from Groq's response
        JsonNode root = mapper.readTree(response.body());
        String content = root.at("/choices/0/message/content").asText().trim();

        // Strip markdown fences if Groq wraps in ```json ... ```
        content = content.replaceAll("(?s)```json", "").replaceAll("(?s)```", "").trim();

        // Validate it's proper JSON before returning
        mapper.readTree(content); // throws JsonProcessingException if invalid

        return content;
    }
}