package com.ispf.server.api;

import com.ispf.server.platform.AutomationIndexStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/automation-index")
public class PlatformAutomationIndexController {

    private final AutomationIndexStatsService statsService;

    public PlatformAutomationIndexController(AutomationIndexStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public AutomationIndexStatsService.AutomationIndexStats stats() {
        return statsService.stats();
    }
}
