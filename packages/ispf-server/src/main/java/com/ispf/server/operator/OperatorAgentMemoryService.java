package com.ispf.server.operator;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class OperatorAgentMemoryService {

    private static final int PROMPT_MEMORY_LIMIT = 14;
    private static final int MAX_CONTENT_LEN = 600;
    private static final int MAX_TOPIC_LEN = 120;

    private final OperatorAgentMemoryStore store;

    public OperatorAgentMemoryService(OperatorAgentMemoryStore store) {
        this.store = store;
    }

    public int count(String appId) {
        return store.countForApp(appId);
    }

    public List<OperatorAgentMemoryRecord> list(String appId, String query, int limit) {
        if (query != null && !query.isBlank()) {
            return store.search(appId, query, limit);
        }
        return store.listForApp(appId, limit);
    }

    public OperatorAgentMemoryRecord remember(
            String appId,
            String kind,
            String topic,
            String content,
            String actor,
            String turnId
    ) {
        String normalizedTopic = normalizeTopic(topic, content);
        String normalizedKind = normalizeKind(kind);
        String normalizedContent = truncate(content, MAX_CONTENT_LEN);
        Instant now = Instant.now();
        String memoryId = store.findByTopic(appId, normalizedTopic)
                .map(OperatorAgentMemoryRecord::memoryId)
                .orElse(OperatorAgentMemoryStore.newMemoryId());
        OperatorAgentMemoryRecord record = new OperatorAgentMemoryRecord(
                memoryId,
                appId,
                normalizedKind,
                normalizedTopic,
                normalizedContent,
                actor,
                turnId,
                0,
                now,
                now
        );
        store.upsert(record);
        return record;
    }

    /**
     * Relevant memories for the current user message — injected into the operator agent prompt.
     * Locale is inferred from Cyrillic in the user message (ru) vs default en.
     */
    public String formatPromptSection(String appId, String userMessage) {
        return formatPromptSection(appId, userMessage, detectLocale(userMessage));
    }

    /**
     * Relevant memories with explicit ru/en prompt header (BL-179).
     */
    public String formatPromptSection(String appId, String userMessage, Locale locale) {
        List<OperatorAgentMemoryRecord> relevant = selectForPrompt(appId, userMessage);
        if (relevant.isEmpty()) {
            return "";
        }
        store.incrementUseCount(relevant.stream().map(OperatorAgentMemoryRecord::memoryId).toList());
        StringBuilder sb = new StringBuilder();
        sb.append(promptHeader(locale));
        for (OperatorAgentMemoryRecord item : relevant) {
            sb.append("- [").append(item.kind()).append("] ")
                    .append(item.topic()).append(": ")
                    .append(item.content().replace('\n', ' '))
                    .append('\n');
        }
        return sb.toString();
    }

    static Locale detectLocale(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Locale.ENGLISH;
        }
        for (int i = 0; i < userMessage.length(); i++) {
            if (Character.UnicodeBlock.of(userMessage.charAt(i)) == Character.UnicodeBlock.CYRILLIC) {
                return Locale.forLanguageTag("ru");
            }
        }
        return Locale.ENGLISH;
    }

    private static String promptHeader(Locale locale) {
        if (locale != null && "ru".equalsIgnoreCase(locale.getLanguage())) {
            return """
                    
                    --- Память приложения (из прошлых смен операторов) ---
                    Используй эти заметки при ответах; предпочитай их догадкам.
                    Вызывай remember_app_memory, когда оператор учит что-то новому.
                    """;
        }
        return """
                
                --- Application memory (learned from past operators) ---
                Use these notes when answering; prefer them over guessing.
                Call remember_app_memory when the user teaches something new.
                """;
    }

    private List<OperatorAgentMemoryRecord> selectForPrompt(String appId, String userMessage) {
        Set<String> seen = new LinkedHashSet<>();
        List<OperatorAgentMemoryRecord> merged = new ArrayList<>();
        if (userMessage != null && !userMessage.isBlank()) {
            for (OperatorAgentMemoryRecord item : store.search(appId, userMessage, PROMPT_MEMORY_LIMIT)) {
                if (seen.add(item.memoryId())) {
                    merged.add(item);
                }
            }
        }
        for (OperatorAgentMemoryRecord item : store.listForApp(appId, PROMPT_MEMORY_LIMIT)) {
            if (merged.size() >= PROMPT_MEMORY_LIMIT) {
                break;
            }
            if (seen.add(item.memoryId())) {
                merged.add(item);
            }
        }
        return merged;
    }

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "fact";
        }
        String normalized = kind.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "fact", "glossary", "preference", "playbook", "correction" -> normalized;
            default -> "fact";
        };
    }

    static String normalizeTopic(String topic, String content) {
        String base = topic != null && !topic.isBlank() ? topic.trim() : content;
        base = base.replaceAll("\\s+", " ");
        if (base.length() > MAX_TOPIC_LEN) {
            base = base.substring(0, MAX_TOPIC_LEN - 1) + "…";
        }
        return base.toLowerCase(Locale.ROOT);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen - 1) + "…";
    }
}
