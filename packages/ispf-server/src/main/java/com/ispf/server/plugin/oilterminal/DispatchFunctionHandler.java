package com.ispf.server.plugin.oilterminal;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.oilterminal.DispatchStatus;
import com.ispf.plugin.oilterminal.OilTerminalConstants;
import com.ispf.server.event.EventService;
import com.ispf.server.function.FunctionHandler;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
public class DispatchFunctionHandler implements FunctionHandler {

    private static final Set<String> HANDLED = Set.of("assign", "start", "complete", "close");

    private static final DataSchema DISPATCH_PAYLOAD = DataSchema.builder("dispatchEventPayload")
            .field("orderNo", FieldType.STRING)
            .field("tankName", FieldType.STRING)
            .field("rackName", FieldType.STRING)
            .field("actualLiters", FieldType.DOUBLE)
            .build();

    private final ObjectManager objectManager;
    private final EventService eventService;

    public DispatchFunctionHandler(ObjectManager objectManager, EventService eventService) {
        this.objectManager = objectManager;
        this.eventService = eventService;
    }

    @Override
    public boolean supports(String objectPath, String functionName) {
        if (!HANDLED.contains(functionName)) {
            return false;
        }
        PlatformObject node = objectManager.tree().findByPath(objectPath).orElse(null);
        return node != null && OilTerminalObjects.isDispatchOrder(node);
    }

    @Override
    public DataRecord invoke(String objectPath, String functionName, DataRecord input) {
        return switch (functionName) {
            case "assign" -> assign(objectPath, input);
            case "start" -> start(objectPath);
            case "complete" -> complete(objectPath, input);
            case "close" -> close(objectPath);
            default -> OilTerminalObjects.failure("Unsupported function: " + functionName);
        };
    }

    private DataRecord assign(String orderPath, DataRecord input) {
        DispatchStatus status = currentStatus(orderPath);
        if (status != DispatchStatus.PLANNED) {
            return OilTerminalObjects.failure("Assign allowed only from planned, current: " + status.wireValue());
        }

        String tankName = readInputString(input, "tankName");
        String rackName = readInputString(input, "rackName");
        if (tankName.isBlank()) {
            tankName = OilTerminalConstants.DEMO_TANK;
        }
        if (rackName.isBlank()) {
            rackName = OilTerminalConstants.DEMO_RACK;
        }

        String tankPath = OilTerminalConstants.tankPath(tankName);
        String rackPath = OilTerminalConstants.rackPath(rackName);
        if (objectManager.tree().findByPath(tankPath).isEmpty()) {
            return OilTerminalObjects.failure("Tank not found: " + tankName);
        }
        if (objectManager.tree().findByPath(rackPath).isEmpty()) {
            return OilTerminalObjects.failure("Rack not found: " + rackName);
        }

        String orderProduct = OilTerminalObjects.stringValue(objectManager, orderPath, "productCode").orElse("");
        String tankProduct = OilTerminalObjects.stringValue(objectManager, tankPath, "productCode").orElse("");
        if (!orderProduct.equals(tankProduct)) {
            return OilTerminalObjects.failure("Product mismatch: order=" + orderProduct + ", tank=" + tankProduct);
        }

        boolean qualityOk = OilTerminalObjects.booleanValue(objectManager, tankPath, "qualityOk").orElse(false);
        if (!qualityOk) {
            return OilTerminalObjects.failure("Tank quality not approved: " + tankName);
        }

        boolean rackBusy = OilTerminalObjects.booleanValue(objectManager, rackPath, "busy").orElse(true);
        if (rackBusy) {
            return OilTerminalObjects.failure("Rack is busy: " + rackName);
        }

        OilTerminalObjects.setString(objectManager, orderPath, "tankName", tankName);
        OilTerminalObjects.setString(objectManager, orderPath, "rackName", rackName);
        OilTerminalObjects.setString(objectManager, orderPath, "status", DispatchStatus.READY.wireValue());
        objectManager.persistNodeTree(orderPath);
        return OilTerminalObjects.success("Assigned " + tankName + " / " + rackName);
    }

    private DataRecord start(String orderPath) {
        DispatchStatus status = currentStatus(orderPath);
        if (status != DispatchStatus.READY) {
            return OilTerminalObjects.failure("Start allowed only from ready, current: " + status.wireValue());
        }

        String rackName = OilTerminalObjects.stringValue(objectManager, orderPath, "rackName")
                .orElseThrow(() -> new IllegalStateException("rackName missing"));
        String rackPath = OilTerminalConstants.rackPath(rackName);
        boolean rackBusy = OilTerminalObjects.booleanValue(objectManager, rackPath, "busy").orElse(true);
        if (rackBusy) {
            return OilTerminalObjects.failure("Rack is busy: " + rackName);
        }

        String orderName = orderPath.substring(orderPath.lastIndexOf('.') + 1);
        OilTerminalObjects.setString(objectManager, orderPath, "status", DispatchStatus.FILLING.wireValue());
        OilTerminalObjects.setString(objectManager, orderPath, "startedAt", Instant.now().toString());
        OilTerminalObjects.setDouble(objectManager, orderPath, "actualLiters", 0.0);
        OilTerminalObjects.setBoolean(objectManager, rackPath, "busy", true);
        OilTerminalObjects.setString(objectManager, rackPath, "currentOrderName", orderName);
        OilTerminalObjects.setDouble(objectManager, rackPath, "totalizerL", 0.0);
        objectManager.persistNodeTree(orderPath);
        objectManager.persistNodeTree(rackPath);

        eventService.fire(orderPath, OilTerminalConstants.EVENT_DISPATCH_STARTED, dispatchPayload(orderPath));
        return OilTerminalObjects.success("Filling started on " + rackName);
    }

    private DataRecord complete(String orderPath, DataRecord input) {
        DispatchStatus status = currentStatus(orderPath);
        if (status != DispatchStatus.FILLING) {
            return OilTerminalObjects.failure("Complete allowed only from filling, current: " + status.wireValue());
        }

        String rackName = OilTerminalObjects.stringValue(objectManager, orderPath, "rackName").orElse("");
        String rackPath = OilTerminalConstants.rackPath(rackName);

        Double actualLiters = readOptionalDouble(input, "actualLiters")
                .or(() -> OilTerminalObjects.doubleValue(objectManager, orderPath, "actualLiters"))
                .or(() -> OilTerminalObjects.doubleValue(objectManager, rackPath, "totalizerL"))
                .orElse(0.0);

        OilTerminalObjects.setDouble(objectManager, orderPath, "actualLiters", actualLiters);
        OilTerminalObjects.setString(objectManager, orderPath, "status", DispatchStatus.COMPLETED.wireValue());
        OilTerminalObjects.setString(objectManager, orderPath, "finishedAt", Instant.now().toString());
        OilTerminalObjects.setBoolean(objectManager, rackPath, "busy", false);
        OilTerminalObjects.setString(objectManager, rackPath, "currentOrderName", "");
        objectManager.persistNodeTree(orderPath);
        objectManager.persistNodeTree(rackPath);

        eventService.fire(orderPath, OilTerminalConstants.EVENT_DISPATCH_COMPLETED, dispatchPayload(orderPath));
        return OilTerminalObjects.success("Filling completed, actualLiters=" + actualLiters);
    }

    private DataRecord close(String orderPath) {
        DispatchStatus status = currentStatus(orderPath);
        if (status != DispatchStatus.COMPLETED) {
            return OilTerminalObjects.failure("Close allowed only from completed, current: " + status.wireValue());
        }
        OilTerminalObjects.setString(objectManager, orderPath, "status", DispatchStatus.CLOSED.wireValue());
        objectManager.persistNodeTree(orderPath);
        return OilTerminalObjects.success("Order closed");
    }

    private DispatchStatus currentStatus(String orderPath) {
        return DispatchStatus.parse(
                OilTerminalObjects.stringValue(objectManager, orderPath, "status").orElse("planned")
        );
    }

    private DataRecord dispatchPayload(String orderPath) {
        return DataRecord.single(DISPATCH_PAYLOAD, Map.of(
                "orderNo", OilTerminalObjects.stringValue(objectManager, orderPath, "orderNo").orElse(""),
                "tankName", OilTerminalObjects.stringValue(objectManager, orderPath, "tankName").orElse(""),
                "rackName", OilTerminalObjects.stringValue(objectManager, orderPath, "rackName").orElse(""),
                "actualLiters", OilTerminalObjects.doubleValue(objectManager, orderPath, "actualLiters").orElse(0.0)
        ));
    }

    private static String readInputString(DataRecord input, String field) {
        if (input == null || input.rows().isEmpty()) {
            return "";
        }
        Object value = input.firstRow().get(field);
        return value != null ? value.toString() : "";
    }

    private static java.util.Optional<Double> readOptionalDouble(DataRecord input, String field) {
        if (input == null || input.rows().isEmpty()) {
            return java.util.Optional.empty();
        }
        Object value = input.firstRow().get(field);
        if (value == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(((Number) value).doubleValue());
    }
}
