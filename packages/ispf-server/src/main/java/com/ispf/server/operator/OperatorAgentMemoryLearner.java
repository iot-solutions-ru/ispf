package com.ispf.server.operator;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic extraction of durable app knowledge from operator conversations (no extra LLM call).
 */
@Component
public class OperatorAgentMemoryLearner {

    private static final Pattern REMEMBER = Pattern.compile(
            "(?ius)(?:запомни|запомните|remember|сохрани\\s+в\\s+память|save\\s+to\\s+memory)\\s*[:\\-–]?\\s*(.+)"
    );
    private static final Pattern CORRECTION = Pattern.compile(
            "(?ius)(?:нет[,!]?\\s+)?(?:не\\s+так|неправильно|имел\\s+в\\s+виду|actually|i\\s+meant)\\s*[:\\-–]?\\s+(.+)"
    );

    private final OperatorAgentMemoryService memoryService;

    public OperatorAgentMemoryLearner(OperatorAgentMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public void learnFromTurn(String appId, String userMessage, String assistantSummary, String actor, String turnId) {
        if (appId == null || appId.isBlank() || userMessage == null || userMessage.isBlank()) {
            return;
        }
        for (MemoryDraft draft : extractDrafts(userMessage, assistantSummary)) {
            memoryService.remember(appId, draft.kind(), draft.topic(), draft.content(), actor, turnId);
        }
    }

    List<MemoryDraft> extractDrafts(String userMessage, String assistantSummary) {
        List<MemoryDraft> drafts = new ArrayList<>();
        Matcher remember = REMEMBER.matcher(userMessage.trim());
        if (remember.find()) {
            String content = remember.group(1).trim();
            if (!content.isEmpty()) {
                drafts.add(new MemoryDraft("fact", slug(content), content));
            }
        }
        if (drafts.isEmpty()) {
            Matcher correction = CORRECTION.matcher(userMessage.trim());
            if (correction.find()) {
                String content = correction.group(1).trim();
                if (content.length() >= 8) {
                    drafts.add(new MemoryDraft("correction", slug(content), content));
                }
            }
        }
        if (drafts.isEmpty() && looksLikeGlossary(userMessage)) {
            drafts.add(new MemoryDraft("glossary", slug(userMessage), userMessage.trim()));
        }
        if (drafts.size() > 2) {
            return drafts.subList(0, 2);
        }
        return drafts;
    }

    private static boolean looksLikeGlossary(String userMessage) {
        String lower = userMessage.toLowerCase(Locale.ROOT);
        return lower.contains(" — ") || lower.contains(" - ") || lower.contains(" это ")
                || lower.startsWith("под ") || lower.contains("называется");
    }

    static String slug(String text) {
        String normalized = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.length() > 80) {
            return normalized.substring(0, 79) + "…";
        }
        return normalized;
    }

    record MemoryDraft(String kind, String topic, String content) {
    }
}
