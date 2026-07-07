package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ML anomaly detection hooks configuration contract (BL-175).
 */
@ConfigurationProperties(prefix = "ispf.ml.anomaly")
public class MlAnomalyProperties {

    /**
     * When true, the server loads {@link com.ispf.core.ml.AnomalyDetectionSpi} implementations from the classpath.
     */
    private boolean enabled = false;

    /**
     * Default score threshold for marking a sample as anomalous (0..1).
     */
    private double defaultThreshold = 0.85;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getDefaultThreshold() {
        return defaultThreshold;
    }

    public void setDefaultThreshold(double defaultThreshold) {
        this.defaultThreshold = defaultThreshold;
    }
}
