package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWidgetPropertiesGuideTest {

    @Test
    void allWidgetTypesHavePropertySpecs() {
        assertEquals(AgentWidgetCatalog.all().size(), AgentWidgetPropertiesGuide.allTypeSpecs().size());
    }

    @Test
    void chartSpecRequiresVariableName() {
        Map<String, Object> spec = AgentWidgetPropertiesGuide.propertiesForType("chart");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) spec.get("required");
        assertTrue(required.contains("variableName"));
        assertTrue(String.valueOf(spec.get("notes")).contains("historyEnabled"));
    }

    @Test
    void progressUsesCurrentAndMaxVariables() {
        Map<String, Object> spec = AgentWidgetPropertiesGuide.propertiesForType("progress");
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) spec.get("required");
        assertTrue(required.contains("currentVariable"));
        assertTrue(required.contains("maxVariable"));
    }

    @Test
    void catalogResponseIncludesPropertySpecForFilteredType() {
        Map<String, Object> result = AgentWidgetCatalog.catalogResponse("object-table", "");
        assertTrue(result.containsKey("propertySpec"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) result.get("propertySpec");
        assertEquals("parent-catalog", spec.get("binding"));
    }

    @Test
    void referenceTextDocumentsValueFieldAndJsonStrings() {
        String text = AgentWidgetPropertiesGuide.referenceText();
        assertTrue(text.contains("valueField"));
        assertTrue(text.contains("columnsJson"));
        assertTrue(text.contains("currentVariable"));
        assertTrue(text.contains("Свойства виджетов"));
        assertFalse(text.contains("%s"));
        assertFalse(text.contains("вЂ"));
    }

    @Test
    void closedFieldTypesCoverDashboardWidgetInterfaces() throws Exception {
        Path ts = Path.of("..", "..", "apps", "web-console", "src", "types", "dashboard.ts");
        if (!Files.exists(ts)) {
            ts = Path.of("apps", "web-console", "src", "types", "dashboard.ts");
        }
        String source = Files.readString(ts);
        source = source.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        source = source.replaceAll("//.*", "");
        Map<String, String> types = AgentWidgetPropertiesGuide.closedWidgetFieldTypes();
        assertEquals("integer", types.get("x"));
        assertEquals("number", types.get("minValue"));
        assertEquals("boolean", types.get("showGrid"));
        Matcher iface = Pattern.compile("export interface (\\w+)[^{]*\\{").matcher(source);
        List<String> missing = new ArrayList<>();
        while (iface.find()) {
            String name = iface.group(1);
            if (!name.equals("DashboardWidgetBase") && !name.endsWith("Widget")) {
                continue;
            }
            int index = iface.end();
            int depth = 1;
            StringBuilder body = new StringBuilder();
            while (index < source.length() && depth > 0) {
                char ch = source.charAt(index);
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                body.append(ch);
                index++;
            }
            Matcher prop = Pattern.compile("(?m)^\\s*(\\w+)\\??:\\s*[^;]+;").matcher(body);
            while (prop.find()) {
                String field = prop.group(1);
                if (!"type".equals(field) && !types.containsKey(field)) {
                    missing.add(name + "." + field);
                }
            }
        }
        assertEquals(List.of(), missing);
    }
}
