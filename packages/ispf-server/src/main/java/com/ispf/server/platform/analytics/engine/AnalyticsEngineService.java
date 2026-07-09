package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsEngine;
import com.ispf.analytics.engine.AnalyticsEvaluatorRegistry;
import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.analytics.engine.LiveVariablePort;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagMetadataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates analytics calculation engine evaluation and tree write-back (BL-203, BL-204, ADR-0041).
 */
@Service
public class AnalyticsEngineService {

    private final AnalyticsProperties analyticsProperties;
    private final AnalyticsTagCatalogService catalogService;
    private final HistorianPort historianPort;
    private final LiveVariablePort liveVariablePort;
    private final AnalyticsDerivedValueWriter derivedValueWriter;
    private final AnalyticsMetricsRecorder metricsRecorder;
    private final AnalyticsTagMetadataService metadataService;
    private final AnalyticsEngine analyticsEngine;

    public AnalyticsEngineService(
            AnalyticsProperties analyticsProperties,
            AnalyticsTagCatalogService catalogService,
            AnalyticsHistorianPortAdapter historianPort,
            AnalyticsLiveVariablePortAdapter liveVariablePort,
            AnalyticsDerivedValueWriter derivedValueWriter,
            AnalyticsMetricsRecorder metricsRecorder,
            AnalyticsTagMetadataService metadataService,
            CelExpressionEvaluator celExpressionEvaluator,
            ExpressionAliasEvaluator expressionAliasEvaluator,
            OeeEvaluator oeeEvaluator
    ) {
        this.analyticsProperties = analyticsProperties;
        this.catalogService = catalogService;
        this.historianPort = historianPort;
        this.liveVariablePort = liveVariablePort;
        this.derivedValueWriter = derivedValueWriter;
        this.metricsRecorder = metricsRecorder;
        this.metadataService = metadataService;
        this.analyticsEngine = new AnalyticsEngine(AnalyticsEvaluatorRegistry.combine(
                AnalyticsEvaluatorRegistry.builtins(),
                celExpressionEvaluator,
                expressionAliasEvaluator,
                oeeEvaluator
        ));
    }

    public boolean isEnabled() {
        return analyticsProperties.engineEnabled();
    }

    @Transactional
    public TickResult evaluateAllEnabled() {
        return evaluateAllEnabled(AnalyticsEvaluationOptions.now(), Instant.now());
    }

    @Transactional
    public TickResult evaluateAllEnabled(AnalyticsEvaluationOptions options, Instant observedAt) {
        if (!isEnabled()) {
            return TickResult.skipped("engine disabled");
        }
        long started = System.nanoTime();
        List<AnalyticsTagDefinition> tags = catalogService.listEnabledTags();
        List<AnalyticsEvaluationResult> engineResults = evaluateWithSession(tags, options);
        int updated = applyEngineResults(engineResults, observedAt);
        metadataService.recordEvaluations(engineResults, observedAt);
        metadataService.propagateQuality(catalogService.listAllTagDefinitions());
        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        metricsRecorder.recordEvaluation(engineResults.size(), latencyMs);
        return new TickResult(true, engineResults.size(), updated, latencyMs, null);
    }

    @Transactional
    public int evaluateTags(List<AnalyticsTagDefinition> tags) {
        List<AnalyticsEvaluationResult> results = evaluateTags(
                tags,
                AnalyticsEvaluationOptions.now(),
                Instant.now()
        );
        return (int) results.stream().filter(r -> "ok".equals(r.status())).count();
    }

    @Transactional
    public List<AnalyticsEvaluationResult> evaluateTags(
            List<AnalyticsTagDefinition> tags,
            AnalyticsEvaluationOptions options,
            Instant observedAt
    ) {
        List<AnalyticsEvaluationResult> results = evaluateWithSession(tags, options);
        applyEngineResults(results, observedAt);
        return results;
    }

    private List<AnalyticsEvaluationResult> evaluateWithSession(
            List<AnalyticsTagDefinition> tags,
            AnalyticsEvaluationOptions options
    ) {
        LiveVariablePort sessionPort = new EvaluationSessionLiveVariablePort(liveVariablePort);
        return analyticsEngine.evaluate(tags, historianPort, sessionPort, options);
    }

    private int applyEngineResults(List<AnalyticsEvaluationResult> results, Instant observedAt) {
        int updated = 0;
        for (AnalyticsEvaluationResult result : results) {
            if (!"ok".equals(result.status())) {
                continue;
            }
            String objectPath = HistorianTagPaths.objectPath(result.tagPath());
            for (Map.Entry<String, Double> entry : result.outputs().entrySet()) {
                derivedValueWriter.write(
                        objectPath,
                        entry.getKey(),
                        AnalyticsLiveVariablePortAdapter.formatNumber(entry.getValue()),
                        observedAt
                );
            }
            updated++;
        }
        return updated;
    }

    public record TickResult(
            boolean ran,
            int evaluated,
            int updated,
            long latencyMs,
            String skipReason
    ) {
        static TickResult skipped(String reason) {
            return new TickResult(false, 0, 0, 0L, reason);
        }
    }
}
