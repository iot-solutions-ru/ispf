package com.ispf.server.api;

import com.ispf.server.platform.time.PlatformTimeZoneResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/timezone")
public class PlatformTimeZoneController {

    private final PlatformTimeZoneResolver timeZoneResolver;

    public PlatformTimeZoneController(PlatformTimeZoneResolver timeZoneResolver) {
        this.timeZoneResolver = timeZoneResolver;
    }

    @GetMapping("/resolve")
    public Map<String, String> resolve(@RequestParam String objectPath) {
        String timeZone = timeZoneResolver.resolve(objectPath);
        return Map.of("objectPath", objectPath, "timeZone", timeZone);
    }
}
