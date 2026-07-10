package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsEngine;
import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.engine.AnalyticsEvaluatorRegistry;
import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.AnalyticsEvaluationResult;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianPort;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.analytics.engine.LiveVariablePort;
import com.ispf.server.config.AnalyticsProperties;
import com.ispf.server.platform.analytics.catalog.AnalyticsTagMetadataService;
import com.ispf.server.platform.analytics.pack.AnalyticsExtensionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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
            OeeEvaluator oeeEvaluator,
            AnalyticsExtensionRegistry extensionRegistry
    ) {
        this.analyticsProperties = analyticsProperties;
        this.catalogService = catalogService;
        this.historianPort = historianPort;
        this.liveVariablePort = liveVariablePort;
        this.derivedValueWriter = derivedValueWriter;
        this.metricsRecorder = metricsRecorder;
        this.metadataService = metadataService;
        List<AnalyticsEvaluator> evaluators = new ArrayList<>();
        evaluators.add(celExpressionEvaluator);
        evaluators.add(expressionAliasEvaluator);
        evaluators.add(oeeEvaluator);
        evaluators.addAll(List.of(extensionRegistry.evaluatorArray()));
        this.analyticsEngine = new AnalyticsEngine(AnalyticsEvaluatorRegistry.combine(
                AnalyticsEvaluatorRegistry.builtins(),
                evaluators.toArray(AnalyticsEvaluator[]::new)
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
        List<AnalyticsTagDefinition> allTags = catalogService.listAllTagDefinitions();
        List<AnalyticsTagDefinition> tags = allTags.stream().filter(AnalyticsTagDefinition::enabled).toList();
        List<AnalyticsEvaluationResult> engineResults = evaluateWithSession(tags, options);
        int updated = applyEngineResults(engineResults, observedAt);
        metadataService.recordEvaluations(engineResults, observedAt);
        metadataService.propagateQuality(allTags);
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

    /**
     * Dry-run evaluation for inspector probe (does not write outputs or metadata).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TagProbeResult probeTag(String tagPath, Instant asOf) {
        if (!isEnabled()) {
            throw new IllegalStateException("Analytics engine is disabled");
        }
        AnalyticsTagDefinition tag = catalogService.findTagDefinition(tagPath)
                .orElseThrow(() -> new IllegalArgumentException("Historian computation not found: " + tagPath));
        long started = System.nanoTime();
        Instant resolvedAsOf = asOf != null ? asOf : Instant.now();
        try {
            List<AnalyticsEvaluationResult> results = evaluateWithSession(
                    List.of(tag),
                    new AnalyticsEvaluationOptions(resolvedAsOf)
            );
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            AnalyticsEvaluationResult result = results.isEmpty()
                    ? AnalyticsEvaluationResult.error(tagPath, tag.helper(), "No evaluation result")
                    : results.getFirst();
            return TagProbeResult.from(result, latencyMs);
        } catch (RuntimeException ex) {
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            return TagProbeResult.from(
                    AnalyticsEvaluationResult.error(tagPath, tag.helper(), message),
                    latencyMs
            );
        }
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

    public record TagProbeResult(
            String tagPath,
            String helper,
            String status,
            Map<String, Double> outputs,
            String message,
            long latencyMs
    ) {
        static TagProbeResult from(AnalyticsEvaluationResult result, long latencyMs) {
            return new TagProbeResult(
                    result.tagPath(),
                    result.helper(),
                    result.status(),
                    result.outputs(),
                    result.message(),
                    latencyMs
            );
        }
    }
}
