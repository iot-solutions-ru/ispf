package com.ispf.server.application;

import com.ispf.server.application.api.SolutionCatalogController;
import com.ispf.server.application.bundle.MarketplaceService;
import com.ispf.server.application.bundle.SolutionCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-06: first light MockMvc pilot (standalone) for a thin catalog controller —
 * template for later {@code @WebMvcTest} migration of API suites.
 */
@ExtendWith(MockitoExtension.class)
class SolutionCatalogApiTest {

    @Mock
    private SolutionCatalogService catalogService;

    @Mock
    private MarketplaceService marketplaceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SolutionCatalogController(catalogService, marketplaceService))
                .build();
    }

    @Test
    void catalogListsInstalledSolutions() throws Exception {
        when(catalogService.catalog()).thenReturn(Map.of(
                "installed", List.of(),
                "installedAnalyticsPacks", List.of()
        ));

        mockMvc.perform(get("/api/v1/solutions/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installed").isArray())
                .andExpect(jsonPath("$.installedAnalyticsPacks").isArray());
    }
}
