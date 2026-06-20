package com.ispf.server.api;

import org.springframework.boot.SpringBootVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PlatformInfoController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "IoT Solutions Platform Framework");
        payload.put("shortName", "ISPF");
        payload.put("version", "0.1.0-SNAPSHOT");
        payload.put("timestamp", Instant.now().toString());
        payload.put("javaVersion", Runtime.version().toString());
        payload.put("springBootVersion", SpringBootVersion.getVersion());
        payload.put("capabilities", new String[]{
                "object-tree",
                "typed-data-records",
                "cel-expressions",
                "device-drivers",
                "rest-api",
                "websocket-events",
                "dashboard-builder",
                "workflow-engine",
                "federation",
                "oidc-rbac"
        });
        return payload;
    }
}
