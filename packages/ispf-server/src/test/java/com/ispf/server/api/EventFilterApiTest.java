package com.ispf.server.api;

import com.ispf.server.eventfilter.EventFilterObjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventFilterApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventFilterObjectService eventFilterObjectService;

    @Test
    void createFromWebConsolePayload() throws Exception {
        eventFilterObjectService.ensureCatalog();

        mockMvc.perform(post("/api/v1/event-filters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "filterId": "12312",
                                  "displayName": "12312",
                                  "description": "",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filterId").value("12312"))
                .andExpect(jsonPath("$.path").value(EventFilterObjectService.EVENT_FILTERS_ROOT + ".f_12312"))
                .andExpect(jsonPath("$.eventNamePattern").value("*"))
                .andExpect(jsonPath("$.sourceObjectPathPattern").value("root.platform.**"));
    }
}
