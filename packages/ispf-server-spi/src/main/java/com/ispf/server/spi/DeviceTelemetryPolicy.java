package com.ispf.server.spi;

import com.ispf.core.object.HistorySampleMode;

/**
 * Per-device telemetry policy read by the object tree. Implemented by the driver runtime.
 */
public interface DeviceTelemetryPolicy {

    long coalesceMs(String devicePath);

    boolean automationEligible(String objectPath, String variableName);

    boolean ingressPayloadLanes(String devicePath);

    void invalidateVariable(String objectPath, String variableName);

    HistorySampleMode historySampleMode(String objectPath, String variableName);

    boolean includePreviousValueInEvent(String objectPath, String variableName);

    void validatePublishModeOverride(String raw);
}
