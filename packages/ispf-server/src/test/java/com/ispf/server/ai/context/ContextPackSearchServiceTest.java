package com.ispf.server.ai.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextPackSearchServiceTest {

    @Mock
    private ContextPackService contextPackService;

    private ContextPackSearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new ContextPackSearchService(contextPackService);
    }

    @Test
    void findsDriverByTopic() {
        when(contextPackService.loadPack()).thenReturn(Map.of(
                "contextPackVersion", "ispf-0.7.8",
                "driverCatalog", List.of(Map.of(
                        "driverId", "snmp",
                        "name", "SNMP",
                        "description", "SNMP v1/v2c/v3 polling",
                        "keywords", "snmp oid community"
                ))
        ));

        Map<String, Object> result = searchService.search("snmp community", "drivers");

        assertEquals("OK", result.get("status"));
        List<?> hits = (List<?>) result.get("hits");
        assertTrue(hits.size() >= 1);
    }

    @Test
    void findsReadinessGapsByTopic() {
        when(contextPackService.loadPack()).thenReturn(Map.of(
                "contextPackVersion", "ispf-0.9.166",
                "competitiveGapIndex", List.of(Map.of(
                        "rank", 1,
                        "dimension", "Ecosystem / marketplace",
                        "gap", 5.0,
                        "current", 5.0,
                        "target", 10.0,
                        "keywords", "marketplace competitive gap scorecard"
                ))
        ));

        Map<String, Object> result = searchService.search("marketplace readiness gap", "gaps");

        assertEquals("OK", result.get("status"));
        List<?> hits = (List<?>) result.get("hits");
        assertTrue(hits.size() >= 1);
        assertTrue(hits.getFirst().toString().contains("Ecosystem"));
    }

    @Test
    void returnsExampleBundleSubset() {
        when(contextPackService.loadPack()).thenReturn(Map.of(
                "examples", List.of(Map.of(
                        "appId", "mes-reference",
                        "packageId", "mes-reference",
                        "path", "examples/mes-reference/bundle.json",
                        "manifest", Map.of(
                                "version", "1.0.0",
                                "functions", List.of(Map.of("name", "mes_listOrders")),
                                "objects", List.of(Map.of("name", "mes-rack-01"))
                        )
                ))
        ));

        Map<String, Object> result = searchService.exampleBundle("mes-reference", List.of("functions"));

        assertEquals("OK", result.get("status"));
        assertTrue(result.toString().contains("mes_listOrders"));
    }
}
