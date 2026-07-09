package com.ispf.analytics.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates analytics tags in DAG order (BL-203).
 */
public final class AnalyticsEngine {

    private final AnalyticsEvaluatorRegistry registry;

    public AnalyticsEngine(AnalyticsEvaluatorRegistry registry) {
        this.registry = registry;
    }

    public static AnalyticsEngine withBuiltins() {
        return new AnalyticsEngine(AnalyticsEvaluatorRegistry.builtins());
    }

    public AnalyticsDag buildDag(List<AnalyticsTagDefinition> tags) {
        return AnalyticsDagBuilder.build(tags);
    }

    public List<AnalyticsEvaluationResult> evaluate(
            List<AnalyticsTagDefinition> tags,
            HistorianPort historian,
            LiveVariablePort live
    ) {
        return evaluate(tags, historian, live, AnalyticsEvaluationOptions.now());
    }

    public List<AnalyticsEvaluationResult> evaluate(
            List<AnalyticsTagDefinition> tags,
            HistorianPort historian,
            LiveVariablePort live,
            AnalyticsEvaluationOptions options
    ) {
        AnalyticsDag dag = buildDag(tags);
        List<AnalyticsEvaluationResult> results = new ArrayList<>(dag.orderedTags().size());
        for (AnalyticsTagDefinition tag : dag.orderedTags()) {
            if (!tag.enabled()) {
                results.add(AnalyticsEvaluationResult.skipped(tag.tagPath(), tag.helper(), "disabled"));
                continue;
            }
            AnalyticsEvaluator evaluator = registry.find(tag.helper())
                    .orElse(null);
            if (evaluator == null) {
                results.add(AnalyticsEvaluationResult.skipped(
                        tag.tagPath(),
                        tag.helper(),
                        "Unknown helper: " + tag.helper()
                ));
                continue;
            }
            try {
                AnalyticsEvaluationResult result = evaluator.evaluate(tag, historian, live, options);
                if ("ok".equals(result.status())) {
                    for (Map.Entry<String, Double> entry : result.outputs().entrySet()) {
                        live.writeNumeric(tag.tagPath(), entry.getKey(), "value", entry.getValue());
                    }
                }
                results.add(result);
            } catch (RuntimeException ex) {
                results.add(AnalyticsEvaluationResult.error(tag.tagPath(), tag.helper(), ex.getMessage()));
            }
        }
        return results;
    }
}
