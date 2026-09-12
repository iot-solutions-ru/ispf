package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObjectSearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchesFullTreeNotOnlyDirectChildren() throws Exception {
        mockMvc.perform(get("/api/v1/objects/search").param("q", "demo-sensor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchCount").isNumber())
                .andExpect(jsonPath("$.objects[*].path", hasItem("root.platform.dashboards.demo-sensor")))
                .andExpect(jsonPath("$.objects[*].path", hasItem("root.platform.dashboards")));
    }

    @Test
    void shortQueryReturnsEmptyRatherThanDumpingTheTree() throws Exception {
        mockMvc.perform(get("/api/v1/objects/search").param("q", "d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchCount").value(0))
                .andExpect(jsonPath("$.objects").isEmpty());
    }
}
