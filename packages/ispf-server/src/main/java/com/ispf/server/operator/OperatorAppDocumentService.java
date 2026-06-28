package com.ispf.server.operator;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class OperatorAppDocumentService {

    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 120_000;
    private static final int PROMPT_DOC_LIMIT = 6;
    private static final int PROMPT_EXCERPT_CHARS = 800;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "csv", "json", "xml", "html", "htm", "yaml", "yml", "log", "rst"
    );

    private final OperatorAppDocumentStore store;

    public OperatorAppDocumentService(OperatorAppDocumentStore store) {
        this.store = store;
    }

    public int count(String appId) {
        return store.countForApp(appId);
    }

    public List<Map<String, Object>> listMetadata(String appId, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OperatorAppDocumentRecord doc : store.listForApp(appId, limit)) {
            rows.add(toMetadataMap(doc));
        }
        return rows;
    }

    public Optional<OperatorAppDocumentRecord> get(String appId, String docId) {
        return store.findById(appId, docId);
    }

    public List<OperatorAppDocumentRecord> search(String appId, String query, int limit) {
        if (query != null && !query.isBlank()) {
            return store.search(appId, query, limit);
        }
        return store.listForApp(appId, limit);
    }

    public OperatorAppDocumentRecord upload(
            String appId,
            MultipartFile file,
            String description
    ) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File too large (max 2 MB)");
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        validateExtension(filename);
        String text = decodeText(file.getBytes(), filename);
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS) + "\n…[truncated]";
        }
        Instant now = Instant.now();
        OperatorAppDocumentRecord record = new OperatorAppDocumentRecord(
                OperatorAppDocumentStore.newDocId(),
                appId,
                filename,
                file.getContentType(),
                normalizeDescription(description),
                text,
                file.getSize(),
                now,
                now
        );
        store.insert(record);
        return record;
    }

    public void delete(String appId, String docId) {
        store.delete(appId, docId);
    }

    /**
     * Injects document catalog + excerpts and optional admin instructions into the operator agent prompt.
     */
    public String formatPromptSection(String appId, String userMessage, String agentInstructions) {
        StringBuilder sb = new StringBuilder();
        if (agentInstructions != null && !agentInstructions.isBlank()) {
            sb.append("\n--- Operator instructions (from app admin) ---\n");
            sb.append(truncate(agentInstructions.trim(), 4000)).append('\n');
        }
        List<OperatorAppDocumentRecord> relevant = selectForPrompt(appId, userMessage);
        if (relevant.isEmpty()) {
            return sb.toString();
        }
        sb.append("\n--- Application knowledge base (uploaded documents) ---\n");
        sb.append("Prefer these over guessing. Use read_app_document for full text, search_app_documents to find more.\n");
        for (OperatorAppDocumentRecord doc : relevant) {
            sb.append("• ").append(doc.filename());
            if (doc.description() != null && !doc.description().isBlank()) {
                sb.append(" — ").append(doc.description().replace('\n', ' '));
            }
            sb.append(" [docId=").append(doc.docId()).append("]\n");
            sb.append("  ").append(excerpt(doc.contentText())).append('\n');
        }
        return sb.toString();
    }

    private List<OperatorAppDocumentRecord> selectForPrompt(String appId, String userMessage) {
        Set<String> seen = new LinkedHashSet<>();
        List<OperatorAppDocumentRecord> merged = new ArrayList<>();
        if (userMessage != null && !userMessage.isBlank()) {
            for (OperatorAppDocumentRecord doc : store.search(appId, userMessage, PROMPT_DOC_LIMIT)) {
                if (seen.add(doc.docId())) {
                    merged.add(doc);
                }
            }
        }
        for (OperatorAppDocumentRecord doc : store.listForApp(appId, PROMPT_DOC_LIMIT)) {
            if (merged.size() >= PROMPT_DOC_LIMIT) {
                break;
            }
            if (seen.add(doc.docId())) {
                merged.add(doc);
            }
        }
        return merged;
    }

    private static Map<String, Object> toMetadataMap(OperatorAppDocumentRecord doc) {
        return Map.of(
                "docId", doc.docId(),
                "filename", doc.filename(),
                "mimeType", doc.mimeType() != null ? doc.mimeType() : "",
                "description", doc.description() != null ? doc.description() : "",
                "byteSize", doc.byteSize(),
                "charCount", doc.contentText() != null ? doc.contentText().length() : 0,
                "updatedAt", doc.updatedAt().toString()
        );
    }

    private static String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String oneLine = text.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return truncate(oneLine, PROMPT_EXCERPT_CHARS);
    }

    private static String decodeText(byte[] bytes, String filename) {
        Charset charset = StandardCharsets.UTF_8;
        String text = new String(bytes, charset);
        if (text.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("Binary files are not supported; use text or markdown");
        }
        return text;
    }

    private static void validateExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            throw new IllegalArgumentException("Unsupported file type; allowed: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
        String ext = lower.substring(dot + 1);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Unsupported file type ." + ext + "; allowed: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "document.txt";
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._\\-а-яА-ЯёЁ ]", "_").trim();
        return name.isBlank() ? "document.txt" : name;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return truncate(description.trim(), 512);
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 1) + "…";
    }
}
