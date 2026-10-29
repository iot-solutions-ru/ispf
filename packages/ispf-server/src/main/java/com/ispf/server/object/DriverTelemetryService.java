package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.event.TelemetryEventJournalFastPath;
import com.ispf.server.function.MqttGatewayIngressDispatchService;
import com.ispf.server.history.TelemetryHistorianFastPath;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Driver/runtime telemetry ingress: journal/historian fast paths, gateway dispatch,
 * coalesce queue. Extracted from {@link ObjectManager} so config CRUD stays separate
 * from high-frequency RAM updates.
 */
@Service
public class DriverTelemetryService {

    private final ObjectManager objectManager;
    private final RuntimeTelemetryCoalescer telemetryCoalescer;
    private final TelemetryIngressDispatcher telemetryIngressDispatcher;
    private final MqttGatewayIngressDispatchService gatewayIngressDispatch;
    private final TelemetryHistorianFastPath historianFastPath;
    private final TelemetryEventJournalFastPath eventJournalFastPath;

    public DriverTelemetryService(
            @Lazy ObjectManager objectManager,
            @Lazy RuntimeTelemetryCoalescer telemetryCoalescer,
            @Lazy TelemetryIngressDispatcher telemetryIngressDispatcher,
            @Lazy MqttGatewayIngressDispatchService gatewayIngressDispatch,
            @Lazy TelemetryHistorianFastPath historianFastPath,
            @Lazy TelemetryEventJournalFastPath eventJournalFastPath
    ) {
        this.objectManager = objectManager;
        this.telemetryCoalescer = telemetryCoalescer;
        this.telemetryIngressDispatcher = telemetryIngressDispatcher;
        this.gatewayIngressDispatch = gatewayIngressDispatch;
        this.historianFastPath = historianFastPath;
        this.eventJournalFastPath = eventJournalFastPath;
    }

    public Variable setDriverTelemetryValue(String path, String name, DataRecord value) {
        return setDriverTelemetryValue(path, name, value, null);
    }

    public Variable setDriverTelemetryValue(String path, String name, DataRecord value, Instant observedAt) {
        DataRecord stored = objectManager.alignDriverTelemetry(path, name, value);
        if (eventJournalFastPath.isEligible(path, name)
                && eventJournalFastPath.tryFire(path, name, stored, observedAt)) {
            return objectManager.resolveDriverTelemetryVariable(path, name, stored);
        }
        if (historianFastPath.isHistorianOnlyEligible(path, name)
                && historianFastPath.tryPublish(path, name, stored, observedAt)) {
            // Keep live values updated: gateway lastIngress and loadtest ingress gates read RAM.
            // Still skip the object-change bus for historian-only TELEMETRY_ONLY ticks.
            Variable variable = objectManager.applyDriverTelemetryInMemory(path, name, stored);
            gatewayIngressDispatch.tryScheduleDispatch(path, name, stored);
            return variable;
        }
        Variable variable = objectManager.applyDriverTelemetryInMemory(path, name, stored);
        if (gatewayIngressDispatch.tryScheduleDispatch(path, name, stored)) {
            return variable;
        }
        if (telemetryIngressDispatcher.isQueueEnabled()) {
            telemetryIngressDispatcher.submit(path, name, stored, observedAt);
        } else {
            telemetryCoalescer.recordUpdate(path, name, stored, observedAt);
        }
        return variable;
    }
}
