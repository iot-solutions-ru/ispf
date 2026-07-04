package com.ispf.server.history;

import com.ispf.core.model.DataRecord;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.driver.TelemetryPublishMode;
import com.ispf.server.object.CoalescedTelemetryUpdate;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.server.object.pubsub.VariableChangeInterest;
import com.ispf.server.object.pubsub.VariableChangeSubscriptionRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skips the object-change bus for high-rate {@code TELEMETRY_ONLY} historian-only updates.
 */
@Service
public class TelemetryHistorianFastPath {

    private static final long ELIGIBILITY_CACHE_TTL_MS = 10_000;

    private final RuntimeTelemetryProperties runtimeTelemetryProperties;
    private final DeviceTelemetryPolicyService telemetryPolicyService;
    private final VariableChangeSubscriptionRegistry subscriptionRegistry;
    private final VariableHistoryService variableHistoryService;
    private final ObjectChangePublicationService publicationService;
    private final ConcurrentHashMap<String, EligibilityCacheEntry> eligibilityCache = new ConcurrentHashMap<>();

    public TelemetryHistorianFastPath(
            RuntimeTelemetryProperties runtimeTelemetryProperties,
            DeviceTelemetryPolicyService telemetryPolicyService,
            VariableChangeSubscriptionRegistry subscriptionRegistry,
            VariableHistoryService variableHistoryService,
            ObjectChangePublicationService publicationService
    ) {
        this.runtimeTelemetryProperties = runtimeTelemetryProperties;
        this.telemetryPolicyService = telemetryPolicyService;
        this.subscriptionRegistry = subscriptionRegistry;
        this.variableHistoryService = variableHistoryService;
        this.publicationService = publicationService;
    }

    /**
     * Cached check: {@code TELEMETRY_ONLY} device with historian interest and no bus side-effects.
     */
    public boolean isHistorianOnlyEligible(String objectPath, String variableName) {
        if (!runtimeTelemetryProperties.isFastHistorianPath()) {
            return false;
        }
        if (telemetryPolicyService.publishMode(objectPath) != TelemetryPublishMode.TELEMETRY_ONLY) {
            return false;
        }
        String cacheKey = objectPath + "|" + variableName;
        long nowMs = System.currentTimeMillis();
        EligibilityCacheEntry cached = eligibilityCache.get(cacheKey);
        if (cached != null && cached.eligible() && nowMs - cached.loadedAtMs() < ELIGIBILITY_CACHE_TTL_MS) {
            return true;
        }
        VariableChangeInterest interest = subscriptionRegistry.interest(objectPath, variableName);
        boolean eligible = interest.historian() && !needsBus(objectPath, interest);
        if (eligible) {
            eligibilityCache.put(cacheKey, new EligibilityCacheEntry(true, nowMs));
        } else {
            eligibilityCache.remove(cacheKey);
        }
        return eligible;
    }

    /**
     * @return true when historian and any optional side-effects were handled without the telemetry bus
     */
    public boolean tryPublish(String objectPath, String variableName, DataRecord value, Instant observedAt) {
        if (!isHistorianOnlyEligible(objectPath, variableName)) {
            return false;
        }
        variableHistoryService.recordFromDataRecordTrusted(objectPath, variableName, value, observedAt);
        return true;
    }

    /** Batch historian enqueue for ingress-drained lanes (L3 → L5). */
    public void publishBatch(List<CoalescedTelemetryUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        if (updates.size() == 1) {
            CoalescedTelemetryUpdate update = updates.getFirst();
            variableHistoryService.recordFromDataRecordTrusted(
                    update.path(),
                    update.variableName(),
                    update.value(),
                    update.observedAt()
            );
            return;
        }
        variableHistoryService.recordFromDataRecordsTrusted(updates);
    }

    private boolean needsBus(String objectPath, VariableChangeInterest interest) {
        if (telemetryPolicyService.publishMode(objectPath) == TelemetryPublishMode.TELEMETRY_ONLY) {
            return false;
        }
        return interest.uiRefresh()
                || (telemetryPolicyService.automationEligible(objectPath) && interest.automation());
    }

    private record EligibilityCacheEntry(boolean eligible, long loadedAtMs) {}
}
