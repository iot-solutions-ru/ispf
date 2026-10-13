package com.ispf.server.api;

import com.ispf.driver.ingress.IngressElasticSettings;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.config.HistorianTierProperties;
import com.ispf.server.config.ObjectChangeProperties;
import com.ispf.server.config.ReplicaCapabilitySet;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.config.VariableHistoryProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADR-0033: snapshot of the replica profile and pool sizes this JVM actually resolved.
 * Numbers only — not an environment dump.
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformEffectiveConfigController {

    /** Optional named overlay ({@code production}, {@code demo-idle}, {@code edge}, {@code throughput}). */
    static final String DEPLOY_PROFILE_PROPERTY = "ispf.deploy.profile";

    private final ClusterProperties clusterProperties;
    private final ObjectChangeProperties objectChangeProperties;
    private final RuntimeTelemetryProperties runtimeTelemetryProperties;
    private final VariableHistoryProperties variableHistoryProperties;
    private final EventJournalProperties eventJournalProperties;
    private final HistorianTierProperties historianTierProperties;
    private final Environment environment;

    public PlatformEffectiveConfigController(
            ClusterProperties clusterProperties,
            ObjectChangeProperties objectChangeProperties,
            RuntimeTelemetryProperties runtimeTelemetryProperties,
            VariableHistoryProperties variableHistoryProperties,
            EventJournalProperties eventJournalProperties,
            HistorianTierProperties historianTierProperties,
            Environment environment
    ) {
        this.clusterProperties = clusterProperties;
        this.objectChangeProperties = objectChangeProperties;
        this.runtimeTelemetryProperties = runtimeTelemetryProperties;
        this.variableHistoryProperties = variableHistoryProperties;
        this.eventJournalProperties = eventJournalProperties;
        this.historianTierProperties = historianTierProperties;
        this.environment = environment;
    }

    @GetMapping("/effective-config")
    public Map<String, Object> effectiveConfig() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile", profile());
        payload.put("resolvedPools", resolvedPools());
        return payload;
    }

    private Map<String, Object> profile() {
        ReplicaCapabilitySet capabilities = clusterProperties.effectiveCapabilities();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("replicaProfile", capabilities.profile().externalName());
        profile.put("replicaCapabilities", capabilities.externalNames());
        profile.put("clusterEnabled", clusterProperties.enabled());
        putIfPresent(profile, "deployProfile", environment.getProperty(DEPLOY_PROFILE_PROPERTY));
        putIfPresent(profile, "historianDeployProfile", historianTierProperties.getDeployProfile());
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            profile.put("activeProfiles", List.of(activeProfiles));
        }
        return profile;
    }

    private Map<String, Object> resolvedPools() {
        Map<String, Object> pools = new LinkedHashMap<>();
        IngressElasticSettings scheduled = clusterProperties.resolvedScheduledPoolElastic();
        pools.put("clusterScheduled", pool(scheduled.resolvedMinWorkers(), scheduled.resolvedMaxWorkers()));
        pools.put("clusterJobs", pool(
                clusterProperties.resolvedJobMaxConcurrentMin(),
                clusterProperties.resolvedJobMaxConcurrentMax()));
        pools.put("objectChange", pool(
                objectChangeProperties.resolvedWorkerThreadsMin(),
                objectChangeProperties.resolvedWorkerThreadsMax()));
        pools.put("objectChangeTelemetry", pool(
                objectChangeProperties.resolvedTelemetryWorkerThreadsMin(),
                objectChangeProperties.resolvedTelemetryWorkerThreadsMax()));
        pools.put("objectChangeAutomation", pool(
                objectChangeProperties.resolvedAutomationWorkerThreadsMin(),
                objectChangeProperties.resolvedAutomationWorkerThreadsMax()));
        IngressElasticSettings coalesce = objectChangeProperties.resolvedCoalesceSchedulerElastic();
        pools.put("objectChangeCoalesce", pool(coalesce.resolvedMinWorkers(), coalesce.resolvedMaxWorkers()));
        pools.put("runtimeTelemetryIngress", pool(
                runtimeTelemetryProperties.resolvedIngressWorkerThreadsMin(),
                runtimeTelemetryProperties.resolvedIngressWorkerThreadsMax()));
        pools.put("runtimeTelemetryCoalesce", pool(
                runtimeTelemetryProperties.resolvedCoalesceSchedulerThreadsMin(),
                runtimeTelemetryProperties.resolvedCoalesceSchedulerThreadsMax()));
        pools.put("variableHistoryWriters", pool(
                variableHistoryProperties.resolvedWriterThreadsMin(),
                variableHistoryProperties.resolvedWriterThreadsMax()));
        pools.put("eventJournalWriters", pool(
                eventJournalProperties.resolvedWriterThreadsMin(),
                eventJournalProperties.resolvedWriterThreadsMax()));
        return pools;
    }

    private static Map<String, Integer> pool(int min, int max) {
        Map<String, Integer> row = new LinkedHashMap<>();
        row.put("min", min);
        row.put("max", max);
        return row;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }
}
