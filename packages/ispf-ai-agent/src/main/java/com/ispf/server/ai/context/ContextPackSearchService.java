package com.ispf.server.ai.context;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Scored search over ContextPack indices (doc chunks, features, examples, drivers).
 * Hybrid mode blends lexical scores with cosine similarity on pack chunk embeddings.
 */
@Service
public class ContextPackSearchService {

    private static final int MAX_HITS = 8;
    private static final double LEXICAL_WEIGHT = 0.45;
    private static final double VECTOR_WEIGHT = 0.55;

    private final ContextPackService contextPackService;
    private final ContextPackEmbeddingClient embeddingClient;

    public ContextPackSearchService(
            ContextPackService contextPackService,
            ContextPackEmbeddingClient embeddingClient
    ) {
        this.contextPackService = contextPackService;
        this.embeddingClient = embeddingClient;
    }

    public Map<String, Object> search(String query, String topic) {
        return search(query, topic, "hybrid");
    }

    public Map<String, Object> search(String query, String topic, String mode) {
        String normalizedTopic = topic == null || topic.isBlank() ? "all" : topic.trim().toLowerCase(Locale.ROOT);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        boolean queryDefaultedFromTopic = false;
        // Studio agents often call topic-only (prompts historically omitted query=). Treat a specific
        // topic as the query so we do not burn turns on "query is required" retries.
        if (q.isBlank() && !normalizedTopic.equals("all")) {
            q = normalizedTopic;
            queryDefaultedFromTopic = true;
        }
        if (q.isBlank()) {
            return Map.of(
                    "status", "ERROR",
                    "error", "query is required (or pass a specific topic; topic-only defaults query to that topic)",
                    "hint", "Example: search_context query=bindings topic=agent-knowledge "
                            + "— or topic=agent-knowledge alone."
            );
        }
        String searchMode = normalizeMode(mode);
        Map<String, Object> pack = contextPackService.loadPack();
        List<Map<String, Object>> hits = new ArrayList<>();

        if (matchesTopic(normalizedTopic, "drivers", "all")) {
            collectDriverHits(pack, q, hits);
        }
        if (matchesTopic(normalizedTopic, "features", "all")) {
            collectFeatureHits(pack, q, hits);
        }
        if (matchesTopic(normalizedTopic, "examples", "all")) {
            collectExampleHits(pack, q, hits);
        }
        if (matchesTopic(normalizedTopic, "gaps", "readiness", "all")) {
            collectGapHits(pack, q, hits);
        }
        collectDocChunkHits(pack, q, hits, normalizedTopic, searchMode);
        if (matchesTopic(normalizedTopic, "agent-knowledge", "all")) {
            collectDocCatalogHits(pack, q, hits);
        }
        if (hits.isEmpty()) {
            collectLegacyDocHits(pack, q, hits);
        }

        hits.sort(Comparator.comparingDouble(row -> -((Number) row.get("score")).doubleValue()));
        List<Map<String, Object>> top = hits.stream().limit(MAX_HITS).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        // Echo caller text when provided; only the topic-default path uses normalized q.
        String echoedQuery = queryDefaultedFromTopic
                ? q
                : (query == null ? q : query.trim());
        result.put("query", echoedQuery);
        result.put("topic", normalizedTopic);
        result.put("mode", searchMode);
        if (queryDefaultedFromTopic) {
            result.put("queryDefaultedFromTopic", true);
        }
        result.put("contextPackVersion", pack.getOrDefault("contextPackVersion", "unknown"));
        result.put("embeddingModel", pack.getOrDefault("embeddingModel", ""));
        result.put("hits", top);
        result.put("hitCount", top.size());
        result.put(
                "hint",
                "Use these short hits — do not re-call search_context with the same query. "
                        + "Next: list_objects / mutate tools, or finish with Markdown."
        );
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> driverHelp(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return Map.of("status", "ERROR", "error", "driverId is required");
        }
        String id = driverId.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> pack = contextPackService.loadPack();
        Object catalog = pack.get("driverCatalog");
        if (catalog instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row && id.equals(String.valueOf(row.get("driverId")).toLowerCase(Locale.ROOT))) {
                    Map<String, Object> result = new LinkedHashMap<>(castMap(row));
                    result.put("status", "OK");
                    return result;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("driverId", driverId);
        result.put("hint", "Use list_drivers and search_context topic=drivers query=" + driverId);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listExampleSummaries() {
        Map<String, Object> pack = contextPackService.loadPack();
        Object summaries = pack.get("exampleSummaries");
        if (summaries instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    result.add(castMap(row));
                }
            }
            return result;
        }
        Object examples = pack.get("examples");
        List<Map<String, Object>> fallback = new ArrayList<>();
        if (examples instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> example) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("appId", example.get("packageId") != null ? example.get("packageId") : example.get("appId"));
                    row.put("version", example.get("version"));
                    row.put("sections", example.get("sections"));
                    row.put("path", example.get("path"));
                    fallback.add(row);
                }
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> exampleBundle(String appId, List<String> sections) {
        if (appId == null || appId.isBlank()) {
            return Map.of("status", "ERROR", "error", "appId is required");
        }
        String resolvedAppId = resolveExampleAppId(appId);
        String needle = resolvedAppId.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> pack = contextPackService.loadPack();
        Object examples = pack.get("examples");
        if (!(examples instanceof List<?> list)) {
            return Map.of("status", "ERROR", "error", "No examples in context pack");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> example)) {
                continue;
            }
            String candidate = String.valueOf(
                    example.get("packageId") != null ? example.get("packageId") : example.get("appId")
            ).toLowerCase(Locale.ROOT);
            String path = String.valueOf(example.get("path") != null ? example.get("path") : "").toLowerCase(Locale.ROOT);
            if (!candidate.equals(needle) && !path.contains(needle)) {
                continue;
            }
            Object manifest = example.get("manifest");
            if (!(manifest instanceof Map<?, ?> manifestMap)) {
                return Map.of("status", "ERROR", "error", "Example manifest missing for " + appId);
            }
            Map<String, Object> subset = new LinkedHashMap<>();
            subset.put("appId", appId);
            if (!resolvedAppId.equals(appId.trim())) {
                subset.put("resolvedAppId", resolvedAppId);
            }
            if (sections == null || sections.isEmpty()) {
                subset.put("manifest", castMap(manifestMap));
            } else {
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (String section : sections) {
                    if (section != null && manifestMap.containsKey(section)) {
                        filtered.put(section, manifestMap.get(section));
                    }
                }
                subset.put("manifest", filtered);
            }
            subset.put("status", "OK");
            return subset;
        }
        return Map.of("status", "ERROR", "error", "Example not found: " + appId);
    }

    @SuppressWarnings("unchecked")
    private void collectDriverHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        Object catalog = pack.get("driverCatalog");
        if (!(catalog instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            int score = scoreRow(row, q, Set.of("driverId", "name", "description", "keywords"));
            if (score > 0) {
                hits.add(hit("driver", String.valueOf(row.get("driverId")), score, castMap(row)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectFeatureHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        Object index = pack.get("featureIndex");
        if (!(index instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            int score = scoreRow(row, q, Set.of("id", "title", "description", "keywords"));
            if (score > 0) {
                hits.add(hit("feature", String.valueOf(row.get("id")), score, castMap(row)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectExampleHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        Object summaries = pack.get("exampleSummaries");
        if (summaries instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                int score = scoreRow(row, q, Set.of("appId", "purpose", "keySections", "keywords"));
                if (score > 0) {
                    hits.add(hit("example", String.valueOf(row.get("appId")), score, castMap(row)));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectGapHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        for (Map<String, Object> row : ContextPackService.competitiveGaps(pack)) {
            int score = scoreRow(row, q, Set.of("dimension", "phaseRef", "keywords"));
            if (q.contains("gap") || q.contains("readiness") || q.contains("scorecard") || q.contains("competitive")) {
                score += 10 + (int) Math.round(gapBoost(row));
            }
            if (score > 0) {
                String id = String.valueOf(row.getOrDefault("dimension", row.get("rank")));
                hits.add(hit("gap", id, score, row));
            }
        }
    }

    private static double gapBoost(Map<String, Object> row) {
        Object gap = row.get("gap");
        if (gap instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(gap));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    @SuppressWarnings("unchecked")
    private void collectDocChunkHits(
            Map<String, Object> pack,
            String q,
            List<Map<String, Object>> hits,
            String topic,
            String searchMode
    ) {
        Object chunks = pack.get("docChunks");
        if (!(chunks instanceof List<?> list)) {
            return;
        }
        String embeddingModel = String.valueOf(pack.getOrDefault("embeddingModel", ""));
        int embeddingDims = pack.get("embeddingDims") instanceof Number n ? n.intValue() : 0;
        boolean hybrid = "hybrid".equals(searchMode) || "vector".equals(searchMode);
        Optional<float[]> queryEmbedding = Optional.empty();
        if (hybrid) {
            queryEmbedding = embeddingClient.embedQuery(q, embeddingModel, embeddingDims);
        }
        boolean useVector = queryEmbedding.isPresent();

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String chunkTopic = String.valueOf(row.get("topic") != null ? row.get("topic") : "all");
            if (!topic.equals("all") && !chunkTopic.equals(topic) && !chunkTopic.equals("all")) {
                continue;
            }
            int lexical = scoreRow(row, q, Set.of("id", "title", "keywords", "text"));
            double vectorScore = 0.0;
            if (useVector) {
                float[] chunkVec = ContextPackHashEmbedder.toFloatArray(row.get("embedding"));
                if (chunkVec != null) {
                    vectorScore = ContextPackHashEmbedder.cosine(queryEmbedding.get(), chunkVec);
                }
            }
            double combined;
            if ("vector".equals(searchMode) && useVector) {
                combined = Math.max(0.0, vectorScore) * 100.0;
            } else if (useVector && "hybrid".equals(searchMode)) {
                double lexNorm = Math.min(1.0, lexical / 40.0);
                double vecNorm = Math.max(0.0, vectorScore);
                combined = (LEXICAL_WEIGHT * lexNorm + VECTOR_WEIGHT * vecNorm) * 100.0;
                // Keep weak lexical-only docs discoverable when vector is near zero.
                if (lexical > 0 && combined < lexical) {
                    combined = lexical;
                }
            } else {
                combined = lexical;
            }
            if (combined <= 0) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chunkId", row.get("id"));
            payload.put("title", row.get("title"));
            payload.put("topic", row.get("topic"));
            payload.put("docRef", row.get("id"));
            payload.put("snippet", snippet(String.valueOf(row.get("text")), q));
            if (useVector) {
                payload.put("vectorScore", Math.round(vectorScore * 1000.0) / 1000.0);
                payload.put("lexicalScore", lexical);
            }
            hits.add(hit("doc", String.valueOf(row.get("id")), combined, payload));
        }
    }

    @SuppressWarnings("unchecked")
    private void collectDocCatalogHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        Object catalog = pack.get("docCatalog");
        if (!(catalog instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            int score = scoreRow(row, q, Set.of("id", "title", "path", "keywords"));
            if (score > 0) {
                hits.add(hit("docCatalog", String.valueOf(row.get("id")), score, castMap(row)));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectLegacyDocHits(Map<String, Object> pack, String q, List<Map<String, Object>> hits) {
        Object apiSlice = pack.get("apiSlice");
        if (!(apiSlice instanceof Map<?, ?> slice)) {
            return;
        }
        for (Map.Entry<?, ?> entry : slice.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String text = String.valueOf(entry.getValue());
            if (text.toLowerCase(Locale.ROOT).contains(q)) {
                hits.add(hit("doc", key, 10, Map.of(
                        "section", key,
                        "snippet", snippet(text, q)
                )));
            }
        }
    }

    private static int scoreRow(Map<?, ?> row, String query, Set<String> fields) {
        int score = 0;
        for (String field : fields) {
            Object value = row.get(field);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).toLowerCase(Locale.ROOT);
            if (text.equals(query)) {
                score += 30;
            } else if (text.contains(query)) {
                score += field.equals("title") || field.equals("driverId") || field.equals("appId") ? 25 : 15;
            }
            for (String token : query.split("\\s+")) {
                if (token.length() >= 3 && text.contains(token)) {
                    score += 5;
                }
            }
        }
        return score;
    }

    private static Map<String, Object> hit(String kind, String id, double score, Map<String, Object> payload) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("id", id);
        row.put("score", Math.round(score * 10.0) / 10.0);
        row.put("payload", payload);
        return row;
    }

    private static String snippet(String text, String query) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int idx = text.toLowerCase(Locale.ROOT).indexOf(query);
        if (idx < 0) {
            return text.length() <= 400 ? text : text.substring(0, 397) + "...";
        }
        int start = Math.max(0, idx - 120);
        int end = Math.min(text.length(), idx + query.length() + 280);
        return text.substring(start, end);
    }

    private static boolean matchesTopic(String topic, String... allowed) {
        for (String candidate : allowed) {
            if (topic.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "hybrid";
        }
        String key = mode.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "lexical", "keyword", "text" -> "lexical";
            case "vector", "embedding", "semantic" -> "vector";
            default -> "hybrid";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            target.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return target;
    }

    private static String resolveExampleAppId(String appId) {
        String normalized = appId.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "virtual", "virtual-driver", "virtual-lab", "lab" -> "lab-training";
            default -> appId.trim();
        };
    }
}
