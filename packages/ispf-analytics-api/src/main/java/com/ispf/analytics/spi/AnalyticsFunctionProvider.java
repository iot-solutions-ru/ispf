package com.ispf.analytics.spi;

import com.ispf.analytics.engine.AnalyticsEvaluator;

/**
 * Service Provider Interface for pluggable analytics functions.
 * <p>
 * Providers are discovered via {@link java.util.ServiceLoader} using service file:
 * {@code META-INF/services/com.ispf.analytics.spi.AnalyticsFunctionProvider}.
 * Implementations should provide a public no-arg constructor.
 * </p>
 */
public interface AnalyticsFunctionProvider {

    /**
     * @return immutable descriptor metadata for this analytics function
     */
    AnalyticsFunctionDescriptor getDescriptor();

    /**
     * @return evaluator instance implementing runtime function behavior
     */
    AnalyticsEvaluator createEvaluator();
}
