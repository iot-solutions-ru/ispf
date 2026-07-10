package com.ispf.server.platform.analytics.pack;

import com.ispf.analytics.engine.AnalyticsEvaluator;
import com.ispf.analytics.spi.AnalyticsFunctionDescriptor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsExtensionRegistry {

    private final Map<String, RegisteredAnalyticsFunction> functionsByHelper = new LinkedHashMap<>();

    public synchronized void register(RegisteredAnalyticsFunction function) {
        functionsByHelper.put(function.helperId(), function);
    }

    public synchronized boolean containsHelper(String helperId) {
        return functionsByHelper.containsKey(helperId);
    }

    public synchronized Collection<RegisteredAnalyticsFunction> registeredFunctions() {
        return List.copyOf(functionsByHelper.values());
    }

    public synchronized AnalyticsEvaluator[] evaluatorArray() {
        return functionsByHelper.values().stream()
                .map(RegisteredAnalyticsFunction::evaluator)
                .toArray(AnalyticsEvaluator[]::new);
    }

    public record RegisteredAnalyticsFunction(
            String packId,
            String helperId,
            AnalyticsFunctionDescriptor descriptor,
            AnalyticsEvaluator evaluator
    ) {
    }
}
