package com.ispf.server.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederationPathRemapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void remapsWidgetObjectPathsInLayoutJson() {
        String layoutJson = """
                {
                  "columns": 12,
                  "widgets": [
                    {
                      "id": "temp-value",
                      "type": "value",
                      "objectPath": "root.platform.devices.demo-sensor-01",
                      "parentPath": "root.platform.devices"
                    }
                  ]
                }
                """;
        String remapped = FederationPathRemapper.remapLayoutJson(
                layoutJson,
                "root.platform",
                "root.platform.federation.site-a",
                objectMapper
        );
        assertTrue(remapped.contains("root.platform.federation.site-a.devices.demo-sensor-01"));
        assertTrue(remapped.contains("root.platform.federation.site-a.devices"));
    }

    @Test
    void resolvesLocalCatalogRootFromPaths() {
        assertEquals(
                "root.platform.federation.site-a",
                FederationPathRemapper.localCatalogRoot(
                        "root.platform.federation.site-a.dashboards.demo-sensor",
                        "root.platform.dashboards.demo-sensor",
                        "root.platform"
                )
        );
    }
}
