package com.ispf.server.event;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.TelemetryPublishMode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * High-rate driver ingress: each telemetry update → one async journal event.
 * Skips historian, object-change bus, and HTTP {@code /events/fire}.
 */
@Service
public class TelemetryEventJournalFastPath {

    private static final DataSchema MQTT_PAYLOAD_SCHEMA = DataSchema.builder("mqttPayload")
            .field("raw", FieldType.STRING)
            .build();

    private final DeviceTelemetryPolicyService telemetryPolicyService;
    private final EventService eventService;

    public TelemetryEventJournalFastPath(
            DeviceTelemetryPolicyService telemetryPolicyService,
            EventService eventService
    ) {
        this.telemetryPolicyService = telemetryPolicyService;
        this.eventService = eventService;
    }

    public boolean isEligible(String objectPath) {
        return telemetryPolicyService.publishMode(objectPath) == TelemetryPublishMode.EVENT_JOURNAL_ONLY;
    }

    /**
     * @return true when the update was handled on the event-journal fast path
     */
    public boolean tryFire(String objectPath, String variableName, DataRecord value, Instant observedAt) {
        if (!isEligible(objectPath)) {
            return false;
        }
        String eventName = telemetryPolicyService.bindingFor(objectPath)
                .map(DriverBinding::ingressEventName)
                .orElse(DriverBinding.DEFAULT_INGRESS_EVENT_NAME);
        eventService.fireIngress(objectPath, eventName, buildPayload(value), observedAt);
        return true;
    }

    private static DataRecord buildPayload(DataRecord value) {
        if (value == null || value.rows().isEmpty()) {
            return DataRecord.single(MQTT_PAYLOAD_SCHEMA, Map.of("raw", ""));
        }
        Map<String, Object> row = value.firstRow();
        Object raw = row.get("raw");
        if (raw == null && row.size() == 1) {
            raw = row.values().iterator().next();
        }
        return DataRecord.single(MQTT_PAYLOAD_SCHEMA, Map.of("raw", raw != null ? String.valueOf(raw) : ""));
    }
}
