package com.ispf.server.api;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.server.event.RecentEventCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventExprApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecentEventCache recentEventCache;

    @Test
    void exprParamKeepsMatchingEvents() throws Exception {
        recentEventCache.append(ObjectEvent.of(
                "root.platform.devices.pump-1",
                "celExprHit",
                EventLevel.WARNING,
                DataRecord.single(
                        DataSchema.builder("payload").field("value", FieldType.STRING).build(),
                        Map.of("value", "a")
                )
        ));
        recentEventCache.append(ObjectEvent.of(
                "root.platform.devices.pump-1",
                "celExprMiss",
                EventLevel.INFO,
                DataRecord.single(
                        DataSchema.builder("payload").field("value", FieldType.STRING).build(),
                        Map.of("value", "b")
                )
        ));

        mockMvc.perform(get("/api/v1/events")
                        .param("expr", "payload.eventName == \"celExprHit\"")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventName=='celExprHit')]").exists())
                .andExpect(jsonPath("$[?(@.eventName=='celExprMiss')]").doesNotExist());
    }

    @Test
    void invalidExprReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .param("expr", "payload.")
                        .param("limit", "10"))
                .andExpect(status().isBadRequest());
    }
}
