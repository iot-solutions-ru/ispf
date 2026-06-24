package com.ispf.server.dashboard;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardWidgetNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void stringifiesColumnsJsonWhenAgentPassesArray() throws Exception {
        Map<String, Object> widget = Map.of(
                "id", "rates-table",
                "type", "object-table",
                "columnsJson", List.of(
                        Map.of("variable", "currentRate", "label", "Rate")
                )
        );
        Map<String, Object> normalized = DashboardWidgetNormalizer.normalizeWidget(widget, objectMapper);
        assertTrue(normalized.get("columnsJson") instanceof String);
        assertEquals(
                List.of(Map.of("variable", "currentRate", "label", "Rate")),
                objectMapper.readValue((String) normalized.get("columnsJson"), List.class)
        );
    }

    @Test
    void resolvesLayoutObjectToJsonString() throws Exception {
        String json = DashboardWidgetNormalizer.resolveLayoutJson(
                Map.of(
                        "columns", 12,
                        "rowHeight", 72,
                        "widgets", List.of(Map.of(
                                "id", "t1",
                                "type", "object-table",
                                "columnsJson", List.of(Map.of("variable", "sysName", "label", "Name"))
                        ))
                ),
                objectMapper
        );
        var layout = objectMapper.readTree(json);
        var columnsJson = layout.get("widgets").get(0).get("columnsJson");
        assertTrue(columnsJson.isString());
        var columns = objectMapper.readValue(columnsJson.asString(), List.class);
        assertEquals(1, columns.size());
    }
}
