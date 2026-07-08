package com.ispf.server.api;

import com.ispf.server.config.VariableHistorySloProperties;
import com.ispf.server.history.HistorianQueryMetricsRecorder;
import com.ispf.server.platform.analytics.AssetAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/analytics")
public class PlatformAnalyticsController {

    private final AssetAnalyticsService assetAnalyticsService;
    private final HistorianQueryMetricsRecorder historianQueryMetricsRecorder;
    private final VariableHistorySloProperties sloProperties;

    public PlatformAnalyticsController(
            AssetAnalyticsService assetAnalyticsService,
            HistorianQueryMetricsRecorder historianQueryMetricsRecorder,
            VariableHistorySloProperties sloProperties
    ) {
        this.assetAnalyticsService = assetAnalyticsService;
        this.historianQueryMetricsRecorder = historianQueryMetricsRecorder;
        this.sloProperties = sloProperties;
    }

    @GetMapping("/templates")
    public List<AssetAnalyticsService.AnalyticsTemplate> templates() {
        return assetAnalyticsService.listTemplates();
    }

    /** Historian query SLA snapshot: p50/p95 latency vs documented SLO (BL-161). */
    @GetMapping("/historian-sla")
    public Map<String, Object> historianQuerySla() {
        return historianQueryMetricsRecorder.snapshot(sloProperties).toMap();
    }
}
