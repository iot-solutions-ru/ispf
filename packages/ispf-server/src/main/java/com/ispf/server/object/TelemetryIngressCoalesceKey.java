package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.server.function.MqttGatewayIngressDispatchService;

import java.util.Optional;

/** Shared coalesce lane keys for L3 ingress queue, L4 coalescer, and gateway dispatch. */
public final class TelemetryIngressCoalesceKey {

    private TelemetryIngressCoalesceKey() {}

    public static String laneKey(String path, String variableName, DataRecord value, boolean ingressPayloadLanes) {
        if (MqttGatewayIngressDispatchService.isIngressPayload(value)) {
            Optional<String> topic = MqttGatewayIngressDispatchService.ingressTopic(value);
            if (topic.isPresent() && !topic.get().isBlank()) {
                String base = path + "|" + variableName + "|" + topic.get();
                if (ingressPayloadLanes) {
                    return MqttGatewayIngressDispatchService.ingressRaw(value)
                            .filter(raw -> !raw.isBlank())
                            .map(raw -> base + "|" + payloadLaneSuffix(raw))
                            .orElse(base);
                }
                return base;
            }
        }
        return path + "|" + variableName;
    }

    static String payloadLaneSuffix(String raw) {
        return Integer.toUnsignedString(raw.hashCode(), 36);
    }
}
