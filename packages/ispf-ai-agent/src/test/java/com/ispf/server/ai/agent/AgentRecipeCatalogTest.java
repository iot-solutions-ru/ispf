package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AgentRecipeCatalogTest {

    @Test
    void catalogLoadsGeneratedCatalogWhenPresent() {
        assumeTrue(hasCatalogResource(), "agent-recipes/catalog.json not present in test runtime");
        AgentRecipeCatalog catalog = new AgentRecipeCatalog(new ObjectMapper());
        assertEquals(1410, catalog.size());
    }

    @Test
    void projectIndexHasExpectedCountWhenCatalogPresent() {
        assumeTrue(hasCatalogResource(), "agent-recipes/catalog.json not present in test runtime");
        AgentRecipeCatalog catalog = new AgentRecipeCatalog(new ObjectMapper());
        @SuppressWarnings("unchecked")
        Map<String, Object> projects = catalog.listProjects(null, null, 0, 2000);
        assertEquals(500, ((Number) projects.get("total")).intValue());
    }

    @Test
    void recipeIdsAreUniqueWhenCatalogPresent() {
        assumeTrue(hasCatalogResource(), "agent-recipes/catalog.json not present in test runtime");
        AgentRecipeCatalog catalog = new AgentRecipeCatalog(new ObjectMapper());
        List<String> ids = catalog.all().stream().map(AgentRecipeCatalog.Recipe::id).toList();
        assertEquals(ids.size(), new HashSet<>(ids).size());
    }

    @Test
    void missingCatalogDoesNotBreakConstruction() {
        AgentRecipeCatalog catalog = new AgentRecipeCatalog(new ObjectMapper());
        if (!hasCatalogResource()) {
            assertEquals(0, catalog.size());
            @SuppressWarnings("unchecked")
            Map<String, Object> search = catalog.search(null, null, null, null, 10, 0);
            assertEquals(0, ((Number) search.get("total")).intValue());
            assertTrue(((List<?>) search.get("recipes")).isEmpty());
        }
    }

    private static boolean hasCatalogResource() {
        return AgentRecipeCatalog.class.getResource("/agent-recipes/catalog.json") != null;
    }
}
