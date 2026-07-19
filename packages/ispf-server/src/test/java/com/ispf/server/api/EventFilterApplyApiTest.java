package com.ispf.server.api;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.server.event.RecentEventCache;
import com.ispf.server.eventfilter.EventFilterObjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventFilterApplyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventFilterObjectService eventFilterObjectService;

    @Autowired
    private RecentEventCache recentEventCache;

    @Test
    void applyEndpointReturnsOnlyMatchingEvents() throws Exception {
        eventFilterObjectService.ensureCatalog();
        mockMvc.perform(post("/api/v1/event-filters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "filterId": "apply-demo",
                                  "displayName": "Apply demo",
                                  "eventNamePattern": "filterMatch*",
                                  "sourceObjectPathPattern": "root.platform.devices.**",
                                  "minSeverity": 0,
                                  "maxSeverity": 100,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk());

        String filterPath = EventFilterObjectService.EVENT_FILTERS_ROOT + ".apply-demo";
        recentEventCache.append(ObjectEvent.of(
                "root.platform.devices.pump-1",
                "filterMatchHi",
                EventLevel.WARNING,
                DataRecord.single(
                        DataSchema.builder("payload").field("value", FieldType.STRING).build(),
                        Map.of("value", "a")
                )
        ));
        recentEventCache.append(ObjectEvent.of(
                "root.platform.devices.pump-1",
                "otherEvent",
                EventLevel.INFO,
                DataRecord.single(
                        DataSchema.builder("payload").field("value", FieldType.STRING).build(),
                        Map.of("value", "b")
                )
        ));

        mockMvc.perform(get("/api/v1/event-filters/by-path/events")
                        .param("path", filterPath)
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventName=='filterMatchHi')]").exists())
                .andExpect(jsonPath("$[?(@.eventName=='otherEvent')]").doesNotExist());

        mockMvc.perform(get("/api/v1/events")
                        .param("filterPath", filterPath)
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventName=='filterMatchHi')]").exists())
                .andExpect(jsonPath("$[?(@.eventName=='otherEvent')]").doesNotExist());
    }
}
