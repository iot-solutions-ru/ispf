package com.ispf.server.dashboard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardWidgetPlacementTest {

    @Test
    void preservesCompactFineGridWidgets() {
        Map<String, Object> layout = Map.of(
                "columns", 84,
                "rowHeight", 8,
                "widgets", List.of(
                        Map.of("id", "a", "type", "value", "x", 0, "y", 0, "w", 4, "h", 2),
                        Map.of("id", "b", "type", "value", "x", 4, "y", 0, "w", 4, "h", 2)
                )
        );

        Map<String, Object> normalized = DashboardWidgetPlacement.normalizeLayoutMap(layout);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");

        assertEquals(84, normalized.get("columns"));
        assertEquals(4, widgets.get(0).get("w"));
        assertEquals(2, widgets.get(0).get("h"));
        assertEquals(0, widgets.get(0).get("y"));
        assertEquals(4, widgets.get(1).get("x"));
        assertEquals(0, widgets.get(1).get("y"));
    }

    @Test
    void reflowsOverlappingWidgetsWithoutScaling() {
        Map<String, Object> layout = Map.of(
                "columns", 84,
                "rowHeight", 8,
                "widgets", List.of(
                        Map.of("id", "a", "type", "value", "x", 0, "y", 0, "w", 4, "h", 2),
                        Map.of("id", "b", "type", "value", "x", 0, "y", 0, "w", 4, "h", 2)
                )
        );

        Map<String, Object> normalized = DashboardWidgetPlacement.normalizeLayoutMap(layout);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");

        assertEquals(0, widgets.get(0).get("y"));
        assertEquals(2, widgets.get(1).get("y"));
        assertEquals(4, widgets.get(0).get("w"));
    }

    @Test
    void doesNotMigrateLegacyGridMetadata() {
        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put("columns", 12);
        layout.put("rowHeight", 72);
        layout.put("widgets", List.of(
                Map.of("id", "a", "type", "value", "x", 0, "y", 0, "w", 6, "h", 2)
        ));

        Map<String, Object> normalized = DashboardWidgetPlacement.normalizeLayoutMap(layout);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");

        assertEquals(12, normalized.get("columns"));
        assertEquals(72, normalized.get("rowHeight"));
        assertEquals(6, widgets.get(0).get("w"));
        assertEquals(2, widgets.get(0).get("h"));
    }

    @Test
    void autoPlacesWidgetMissingCoordinates() {
        List<Map<String, Object>> existing = new ArrayList<>();
        existing.add(Map.of("id", "a", "x", 0, "y", 0, "w", 28, "h", 14));

        Map<String, Object> widget = new LinkedHashMap<>(Map.of(
                "id", "b",
                "type", "value",
                "title", "B"
        ));

        Map<String, Object> placed = DashboardWidgetPlacement.prepareNewWidget(
                widget,
                existing,
                84,
                8
        );

        assertEquals(28, placed.get("w"));
        assertEquals(14, placed.get("h"));
        assertEquals(14, placed.get("y"));
    }

    @Test
    void preservesCompactNavRow() {
        Map<String, Object> layout = Map.of(
                "columns", 84,
                "rowHeight", 8,
                "widgets", List.of(
                        Map.of("id", "nav", "type", "dashboard-link", "x", 0, "y", 0, "w", 10, "h", 7),
                        Map.of("id", "host", "type", "value", "x", 10, "y", 0, "w", 24, "h", 7),
                        Map.of("id", "driver", "type", "status-badge", "x", 75, "y", 0, "w", 9, "h", 7)
                )
        );

        Map<String, Object> normalized = DashboardWidgetPlacement.normalizeLayoutMap(layout);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) normalized.get("widgets");

        assertEquals(10, widgets.get(0).get("w"));
        assertEquals(7, widgets.get(0).get("h"));
        assertEquals(0, widgets.get(0).get("y"));
        assertEquals(10, widgets.get(1).get("x"));
        assertEquals(0, widgets.get(1).get("y"));
        assertEquals(75, widgets.get(2).get("x"));
    }

    @Test
    void prepareNewWidgetKeepsExplicitCompactSize() {
        Map<String, Object> widget = new LinkedHashMap<>(Map.of(
                "id", "nav",
                "type", "dashboard-link",
                "title", "Nav",
                "x", 0,
                "y", 0,
                "w", 10,
                "h", 7
        ));

        Map<String, Object> placed = DashboardWidgetPlacement.prepareNewWidget(
                widget,
                List.of(),
                84,
                8
        );

        assertEquals(10, placed.get("w"));
        assertEquals(7, placed.get("h"));
        assertEquals(0, placed.get("x"));
        assertEquals(0, placed.get("y"));
    }

    @Test
    void scadaMimicDefaultsToFullScreen() {
        Map<String, Object> widget = new LinkedHashMap<>(Map.of(
                "id", "mimic",
                "type", "scada-mimic",
                "title", "Mimic",
                "mimicPath", "root.platform.mimics.demo"
        ));

        Map<String, Object> placed = DashboardWidgetPlacement.prepareNewWidget(
                widget,
                List.of(),
                84,
                8
        );

        assertEquals(84, placed.get("w"));
        assertEquals(63, placed.get("h"));
    }
}
