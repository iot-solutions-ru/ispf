package com.ispf.server.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PlatformInfoController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "name", "IoT Solutions Platform Framework",
                "shortName", "ISPF",
                "version", "0.1.0-SNAPSHOT",
                "timestamp", Instant.now().toString(),
                "capabilities", new String[]{
                        "object-tree",
                        "typed-data-records",
                        "cel-expressions",
                        "device-drivers",
                        "rest-api",
                        "websocket-events",
                        "dashboard-builder",
                        "workflow-engine"
                }
        );
    }
}
