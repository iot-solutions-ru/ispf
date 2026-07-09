package com.ispf.analytics.engine;

import com.ispf.analytics.engine.eval.LastEvaluator;
import com.ispf.analytics.engine.eval.MaxWindowEvaluator;
import com.ispf.analytics.engine.eval.MinWindowEvaluator;
import com.ispf.analytics.engine.eval.RateOfChangeEvaluator;
import com.ispf.analytics.engine.eval.RollingAvgEvaluator;
import com.ispf.analytics.engine.eval.TotalizerEvaluator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of built-in analytics evaluators (BL-203).
 */
public final class AnalyticsEvaluatorRegistry {

    private final Map<String, AnalyticsEvaluator> evaluators;

    public AnalyticsEvaluatorRegistry(Map<String, AnalyticsEvaluator> evaluators) {
        this.evaluators = Map.copyOf(evaluators);
    }

    public static AnalyticsEvaluatorRegistry builtins() {
        Map<String, AnalyticsEvaluator> map = new LinkedHashMap<>();
        register(map, new RollingAvgEvaluator());
        register(map, new RateOfChangeEvaluator());
        register(map, new TotalizerEvaluator());
        register(map, new LastEvaluator());
        register(map, new MinWindowEvaluator());
        register(map, new MaxWindowEvaluator());
        return new AnalyticsEvaluatorRegistry(map);
    }

    public static AnalyticsEvaluatorRegistry combine(
            AnalyticsEvaluatorRegistry base,
            AnalyticsEvaluator... extras
    ) {
        Map<String, AnalyticsEvaluator> map = new LinkedHashMap<>();
        for (String helper : base.helpers()) {
            base.find(helper).ifPresent(evaluator -> map.put(helper, evaluator));
        }
        for (AnalyticsEvaluator extra : extras) {
            register(map, extra);
        }
        return new AnalyticsEvaluatorRegistry(map);
    }

    private static void register(Map<String, AnalyticsEvaluator> map, AnalyticsEvaluator evaluator) {
        map.put(evaluator.helper(), evaluator);
    }

    public Optional<AnalyticsEvaluator> find(String helper) {
        return Optional.ofNullable(evaluators.get(helper));
    }

    public List<String> helpers() {
        return List.copyOf(evaluators.keySet());
    }
}
