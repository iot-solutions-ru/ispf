package com.ispf.server.api;

import com.ispf.server.platform.analytics.AssetAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform/analytics")
public class PlatformAnalyticsController {

    private final AssetAnalyticsService assetAnalyticsService;

    public PlatformAnalyticsController(AssetAnalyticsService assetAnalyticsService) {
        this.assetAnalyticsService = assetAnalyticsService;
    }

    @GetMapping("/templates")
    public List<AssetAnalyticsService.AnalyticsTemplate> templates() {
        return assetAnalyticsService.listTemplates();
    }
}
