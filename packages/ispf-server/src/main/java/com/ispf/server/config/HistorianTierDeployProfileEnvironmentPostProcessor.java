package com.ispf.server.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies historian deploy-profile defaults before tier-routing beans are created (BL-202).
 *
 * <p>{@code three-tier} enables warm read/write routing when {@code ispf.historian.tiers.warm.enabled}
 * is not explicitly set. {@code hot-only} keeps JDBC-only path.
 */
public final class HistorianTierDeployProfileEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "ispfHistorianDeployProfileDefaults";

    static final String WARM_ENABLED_KEY = "ispf.historian.tiers.warm.enabled";
    static final String DEPLOY_PROFILE_KEY = "ispf.historian.deploy-profile";
    static final String SINGLE_ENGINE_KEY = "ispf.relational.mssql.single-engine";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String deployProfile = environment.getProperty(DEPLOY_PROFILE_KEY, "three-tier");
        Map<String, Object> overrides = new LinkedHashMap<>();
        if ("mssql-single-engine".equalsIgnoreCase(deployProfile)) {
            if (!environment.containsProperty(WARM_ENABLED_KEY)) {
                overrides.put(WARM_ENABLED_KEY, "false");
            }
            if (!environment.containsProperty(SINGLE_ENGINE_KEY)) {
                overrides.put(SINGLE_ENGINE_KEY, "true");
            }
        } else if (!environment.containsProperty(WARM_ENABLED_KEY)) {
            if ("hot-only".equalsIgnoreCase(deployProfile)) {
                overrides.put(WARM_ENABLED_KEY, "false");
            } else if ("three-tier".equalsIgnoreCase(deployProfile)) {
                overrides.put(WARM_ENABLED_KEY, "true");
            }
        }
        if (overrides.isEmpty()) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
    }
}
