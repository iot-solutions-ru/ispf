package com.ispf.server.certification;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BL-190: certification exam runner stub — grades multiple-choice banks from {@code examples/certification/}.
 */
@Service
public class CertificationExamService {

    private static final double PASS_THRESHOLD = 0.75;
    private static final AtomicLong ATTEMPT_SEQ = new AtomicLong(1);

    private final ObjectMapper objectMapper;

    public CertificationExamService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> submitAttempt(CertificationAttemptRequest request) {
        if (request == null || request.track() == null || request.track().isBlank()) {
            throw new IllegalArgumentException("track is required");
        }
        if (request.level() == null) {
            throw new IllegalArgumentException("level is required");
        }
        if (request.answers() == null || request.answers().isEmpty()) {
            throw new IllegalArgumentException("answers are required");
        }

        Map<String, Object> bank = loadQuestionBank(request.track().trim(), request.level());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) bank.get("questions");
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("Question bank has no questions");
        }

        Map<String, Integer> answerByQuestionId = new LinkedHashMap<>();
        for (CertificationAttemptRequest.Answer answer : request.answers()) {
            if (answer == null || answer.questionId() == null || answer.questionId().isBlank()) {
                continue;
            }
            if (answer.selectedIndex() == null) {
                throw new IllegalArgumentException("selectedIndex is required for " + answer.questionId());
            }
            answerByQuestionId.put(answer.questionId().trim(), answer.selectedIndex());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int correct = 0;
        for (Map<String, Object> question : questions) {
            String questionId = stringValue(question.get("id"));
            Integer selected = answerByQuestionId.get(questionId);
            int correctIndex = numberValue(question.get("correctIndex"));
            boolean isCorrect = selected != null && selected == correctIndex;
            if (isCorrect) {
                correct++;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionId", questionId);
            row.put("topic", question.get("topic"));
            row.put("selectedIndex", selected);
            row.put("correctIndex", correctIndex);
            row.put("correct", isCorrect);
            if (!isCorrect) {
                row.put("reference", question.get("reference"));
            }
            results.add(row);
        }

        int total = questions.size();
        double scoreRatio = total == 0 ? 0.0 : (double) correct / total;
        boolean passed = scoreRatio >= PASS_THRESHOLD;
        String attemptId = "cert-attempt-" + ATTEMPT_SEQ.getAndIncrement() + "-"
                + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        response.put("attemptId", attemptId);
        response.put("track", bank.get("track"));
        response.put("level", bank.get("level"));
        response.put("bankTitle", bank.get("title"));
        response.put("bankVersion", bank.get("version"));
        response.put("score", correct);
        response.put("total", total);
        response.put("scorePercent", Math.round(scoreRatio * 1000.0) / 10.0);
        response.put("passThresholdPercent", PASS_THRESHOLD * 100.0);
        response.put("passed", passed);
        response.put("results", results);
        response.put(
                "message",
                passed
                        ? "Knowledge check passed (stub); practical lab verification still required"
                        : "Below pass threshold; review references and retry"
        );
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadQuestionBank(String track, Object level) {
        Path bankPath = resolveBankPath(track, level);
        if (!Files.isRegularFile(bankPath)) {
            throw new IllegalArgumentException("Unknown certification bank: " + track + " / " + level);
        }
        try {
            return objectMapper.readValue(Files.readString(bankPath), Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load certification bank: " + ex.getMessage(), ex);
        }
    }

    private Path resolveBankPath(String track, Object level) {
        String fileName = switch (track) {
            case "solution-developer" -> switch (String.valueOf(level)) {
                case "1" -> "solution-developer-l1.json";
                case "2" -> "solution-developer-l2.json";
                default -> throw new IllegalArgumentException("Unknown solution-developer level: " + level);
            };
            case "platform-admin" -> {
                String levelKey = String.valueOf(level);
                if ("core".equalsIgnoreCase(levelKey)) {
                    yield "platform-admin-core.json";
                }
                throw new IllegalArgumentException("Unknown platform-admin level: " + level);
            }
            default -> throw new IllegalArgumentException("Unknown certification track: " + track);
        };
        return resolveCertificationDir().resolve(fileName);
    }

    private Path resolveCertificationDir() {
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int depth = 0; depth <= 4; depth++) {
            Path candidate = cwd.resolve("examples/certification");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path parent = cwd.getParent();
            if (parent == null) {
                break;
            }
            cwd = parent;
        }
        return Paths.get("examples/certification");
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int numberValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public record CertificationAttemptRequest(
            String track,
            Object level,
            List<Answer> answers
    ) {
        public record Answer(String questionId, Integer selectedIndex) {
        }
    }
}
