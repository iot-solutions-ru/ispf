package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.server.driver.DriverRuntimeService;

/**
 * Adds driver runtime hints to lite {@link ObjectDto} payloads for explorer tree styling.
 */
public final class ObjectTreeDriverEnricher {

    private ObjectTreeDriverEnricher() {
    }

    public static ObjectDto enrichLite(ObjectDto dto, PlatformObject node, DriverRuntimeService driverRuntimeService) {
        if (node.type() != ObjectType.DEVICE) {
            return dto;
        }
        String driverId = readString(node, "driverId");
        if (driverId == null || driverId.isBlank()) {
            return dto;
        }
        String status = readString(node, "driverStatus");
        if (status == null || status.isBlank()) {
            status = "STOPPED";
        }
        Boolean connected = null;
        if ("RUNNING".equals(status)) {
            connected = driverRuntimeService.status(node.path())
                    .map(DriverRuntimeService.DriverRuntimeStatus::connected)
                    .orElse(false);
        }
        return dto.withDriverRuntime(status, connected);
    }

    private static String readString(PlatformObject node, String name) {
        return node.getVariable(name)
                .flatMap(Variable::value)
                .flatMap(record -> record.rows().stream().findFirst())
                .map(row -> row.get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank())
                .orElse(null);
    }
}
