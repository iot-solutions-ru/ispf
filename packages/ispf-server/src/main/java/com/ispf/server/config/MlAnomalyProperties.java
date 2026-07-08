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

    /**
     * Inclusive lower bound for {@link com.ispf.server.ml.ThresholdAnomalyDetectionSpi}.
     */
    private double thresholdMin = Double.NEGATIVE_INFINITY;

    /**
     * Inclusive upper bound for {@link com.ispf.server.ml.ThresholdAnomalyDetectionSpi}.
     */
    private double thresholdMax = Double.POSITIVE_INFINITY;

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

    public double getThresholdMin() {
        return thresholdMin;
    }

    public void setThresholdMin(double thresholdMin) {
        this.thresholdMin = thresholdMin;
    }

    public double getThresholdMax() {
        return thresholdMax;
    }

    public void setThresholdMax(double thresholdMax) {
        this.thresholdMax = thresholdMax;
    }
}
