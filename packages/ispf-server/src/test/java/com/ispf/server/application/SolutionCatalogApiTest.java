package com.ispf.server.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SolutionCatalogApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogListsInstalledSolutions() throws Exception {
        mockMvc.perform(get("/api/v1/solutions/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installed").isArray())
                .andExpect(jsonPath("$.installedAnalyticsPacks").isArray());
    }
}
