package com.ispf.server.ai.agent;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Catalog of generated platform recipes loaded from classpath JSON.
 */
@Component
final class AgentRecipeCatalog {

    private static final String RESOURCE = "/agent-recipes/catalog.json";
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 250;

    private final List<Recipe> recipes;
    private final List<Recipe> projects;
    private final Map<String, Recipe> byId;

    AgentRecipeCatalog(ObjectMapper objectMapper) {
        CatalogData loaded = loadCatalog(objectMapper);
        this.recipes = loaded.recipes();
        this.projects = loaded.projects();
        Map<String, Recipe> index = new LinkedHashMap<>();
        for (Recipe recipe : this.recipes) {
            index.putIfAbsent(recipe.id(), recipe);
        }
        this.byId = Map.copyOf(index);
    }

    int size() {
        return recipes.size();
    }

    List<Recipe> all() {
        return recipes;
    }

    Optional<Recipe> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id.trim()));
    }

    Map<String, Object> search(
            String query,
            String category,
            String industry,
            String archetype,
            int limit,
            int offset
    ) {
        String queryNorm = normalize(query);
        String categoryNorm = normalize(category);
        String industryNorm = normalize(industry);
        String archetypeNorm = normalize(archetype);
        List<Recipe> filtered = recipes.stream()
                .filter(recipe -> categoryNorm.isEmpty() || categoryNorm.equals(normalize(recipe.category())))
                .filter(recipe -> industryNorm.isEmpty() || industryNorm.equals(normalize(recipe.industry())))
                .filter(recipe -> archetypeNorm.isEmpty() || archetypeNorm.equals(normalize(recipe.archetype())))
                .filter(recipe -> matchesQuery(recipe, queryNorm))
                .toList();
        return pagedDetailResponse(
                "recipes",
                filtered,
                offset,
                limit,
                Map.of(
                        "query", query == null ? "" : query.trim(),
                        "category", category == null ? "" : category.trim(),
                        "industry", industry == null ? "" : industry.trim(),
                        "archetype", archetype == null ? "" : archetype.trim()
                )
        );
    }

    Map<String, Object> listProjects(String industry, String archetype, int offset, int limit) {
        String industryNorm = normalize(industry);
        String archetypeNorm = normalize(archetype);
        List<Recipe> filtered = projects.stream()
                .filter(recipe -> industryNorm.isEmpty() || industryNorm.equals(normalize(recipe.industry())))
                .filter(recipe -> archetypeNorm.isEmpty() || archetypeNorm.equals(normalize(recipe.archetype())))
                .toList();
        return pagedSummaryResponse(
                "projects",
                filtered,
                offset,
                limit,
                Map.of(
                        "industry", industry == null ? "" : industry.trim(),
                        "archetype", archetype == null ? "" : archetype.trim()
                )
        );
    }

    Map<String, Object> indexSummary(int offset, int limit) {
        return pagedSummaryResponse("recipes", recipes, offset, limit, Map.of());
    }

    static Map<String, Object> toDetailRow(Recipe recipe) {
        Map<String, Object> row = baseRow(recipe);
        row.put("layers", recipe.layers());
        row.put("toolChain", recipe.toolChain());
        row.put("verify", recipe.verify());
        if (!recipe.atomicRefs().isEmpty()) {
            row.put("atomicRefs", recipe.atomicRefs());
        }
        return row;
    }

    private Map<String, Object> pagedDetailResponse(
            String key,
            List<Recipe> source,
            int offsetArg,
            int limitArg,
            Map<String, Object> extra
    ) {
        PageWindow window = pageWindow(source.size(), offsetArg, limitArg);
        List<Map<String, Object>> items = source.subList(window.offset(), window.toIndex()).stream()
                .map(AgentRecipeCatalog::toDetailRow)
                .toList();
        Map<String, Object> result = basePageResult(key, source.size(), window, items);
        result.putAll(extra);
        return result;
    }

    private Map<String, Object> pagedSummaryResponse(
            String key,
            List<Recipe> source,
            int offsetArg,
            int limitArg,
            Map<String, Object> extra
    ) {
        PageWindow window = pageWindow(source.size(), offsetArg, limitArg);
        List<Map<String, Object>> items = source.subList(window.offset(), window.toIndex()).stream()
                .map(AgentRecipeCatalog::toSummaryRow)
                .toList();
        Map<String, Object> result = basePageResult(key, source.size(), window, items);
        result.putAll(extra);
        return result;
    }

    private static Map<String, Object> basePageResult(
            String key,
            int total,
            PageWindow window,
            List<Map<String, Object>> items
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("total", total);
        result.put("offset", window.offset());
        result.put("limit", window.limit());
        result.put("count", items.size());
        result.put(key, items);
        return result;
    }

    private static PageWindow pageWindow(int total, int offsetArg, int limitArg) {
        int safeOffset = Math.max(0, offsetArg);
        int safeLimit = limitArg <= 0 ? DEFAULT_LIMIT : Math.min(limitArg, MAX_LIMIT);
        if (safeOffset > total) {
            safeOffset = total;
        }
        int toIndex = Math.min(total, safeOffset + safeLimit);
        return new PageWindow(safeOffset, safeLimit, toIndex);
    }

    private static Map<String, Object> toSummaryRow(Recipe recipe) {
        Map<String, Object> row = baseRow(recipe);
        if (!recipe.layers().isEmpty()) {
            row.put("layers", recipe.layers());
        }
        return row;
    }

    private static Map<String, Object> baseRow(Recipe recipe) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", recipe.id());
        row.put("category", recipe.category());
        row.put("title", recipe.title());
        row.put("oneLiner", recipe.oneLiner());
        row.put("tier", recipe.tier());
        if (recipe.industry() != null) {
            row.put("industry", recipe.industry());
        }
        if (recipe.archetype() != null) {
            row.put("archetype", recipe.archetype());
        }
        return row;
    }

    private static boolean matchesQuery(Recipe recipe, String queryNorm) {
        if (queryNorm.isEmpty()) {
            return true;
        }
        String haystack = String.join(
                " ",
                recipe.id(),
                recipe.category(),
                recipe.title(),
                recipe.oneLiner(),
                recipe.tier(),
                stringOrEmpty(recipe.industry()),
                stringOrEmpty(recipe.archetype()),
                String.join(" ", recipe.layers()),
                String.join(" ", recipe.toolChain()),
                String.join(" ", recipe.verify()),
                String.join(" ", recipe.atomicRefs())
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(queryNorm);
    }

    private CatalogData loadCatalog(ObjectMapper objectMapper) {
        try (InputStream input = AgentRecipeCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                return new CatalogData(List.of(), List.of());
            }
            JsonNode root = objectMapper.readTree(input);
            List<Recipe> loadedRecipes = root.isArray()
                    ? deduplicate(parseRecipes(root))
                    : deduplicate(parseRecipes(resolveArray(root, "recipes", "items", "catalog")));
            List<Recipe> loadedProjects = deduplicate(parseRecipes(resolveArray(root, "projects")));
            if (loadedProjects.isEmpty()) {
                loadedProjects = loadedRecipes.stream()
                        .filter(AgentRecipeCatalog::isProjectRecipe)
                        .toList();
            }
            return new CatalogData(loadedRecipes, loadedProjects);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load agent recipe catalog from " + RESOURCE, ex);
        }
    }

    private static JsonNode resolveArray(JsonNode root, String... keys) {
        if (root == null || root.isMissingNode() || !root.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode candidate = root.path(key);
            if (candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Recipe> parseRecipes(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<Recipe> rows = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            Recipe recipe = parseRecipe(node);
            if (recipe != null) {
                rows.add(recipe);
            }
        }
        return List.copyOf(rows);
    }

    private static List<Recipe> deduplicate(List<Recipe> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<String, Recipe> unique = new LinkedHashMap<>();
        for (Recipe recipe : source) {
            unique.putIfAbsent(recipe.id(), recipe);
        }
        return List.copyOf(unique.values());
    }

    private static Recipe parseRecipe(JsonNode node) {
        String id = requiredText(node, "id");
        if (id.isBlank()) {
            return null;
        }
        return new Recipe(
                id,
                requiredText(node, "category"),
                requiredText(node, "title"),
                requiredText(node, "oneLiner"),
                textList(node, "layers"),
                textList(node, "toolChain"),
                textList(node, "verify"),
                requiredText(node, "tier"),
                optionalText(node, "industry"),
                optionalText(node, "archetype"),
                textList(node, "atomicRefs")
        );
    }

    private static boolean isProjectRecipe(Recipe recipe) {
        String category = normalize(recipe.category());
        return "project".equals(category) || category.startsWith("project-");
    }

    private static List<String> textList(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : value) {
            if (item == null || item.isNull()) {
                continue;
            }
            String text = item.asText("").trim();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return List.copyOf(out);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private static String optionalText(JsonNode node, String field) {
        String value = requiredText(node, field);
        return value.isBlank() ? null : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }

    record Recipe(
            String id,
            String category,
            String title,
            String oneLiner,
            List<String> layers,
            List<String> toolChain,
            List<String> verify,
            String tier,
            String industry,
            String archetype,
            List<String> atomicRefs
    ) {
    }

    private record CatalogData(List<Recipe> recipes, List<Recipe> projects) {
    }

    private record PageWindow(int offset, int limit, int toIndex) {
    }
}
