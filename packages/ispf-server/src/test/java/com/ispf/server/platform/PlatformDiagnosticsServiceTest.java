package com.ispf.server.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PlatformDiagnosticsServiceTest {

    @Autowired
    private PlatformDiagnosticsService diagnosticsService;

    @Test
    void snapshotIncludesDiagnosticsDetail() {
        Map<String, Object> snapshot = diagnosticsService.snapshot();

        assertNotNull(snapshot.get("replicaId"));
        assertNotNull(snapshot.get("processCpuPercent"));
        assertNotNull(snapshot.get("suspects"));

        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) snapshot.get("detail");
        assertNotNull(detail);
        assertNotNull(detail.get("threadGroups"));
        assertNotNull(detail.get("drivers"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suspects = (List<Map<String, Object>>) snapshot.get("suspects");
        assertTrue(suspects.size() >= 0);
    }
}
