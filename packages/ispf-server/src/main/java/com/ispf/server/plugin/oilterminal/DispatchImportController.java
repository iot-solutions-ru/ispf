package com.ispf.server.plugin.oilterminal;

import com.ispf.core.object.ObjectType;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.oilterminal.DispatchStatus;
import com.ispf.plugin.oilterminal.OilTerminalConstants;
import com.ispf.server.object.ObjectManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * ERP stub — import dispatch orders for the reference stand.
 */
@RestController
@RequestMapping("/api/v1/oil/dispatch")
public class DispatchImportController {

    private final ObjectManager objectManager;
    private final ModelEngine modelEngine;
    private final ModelRegistry modelRegistry;

    public DispatchImportController(
            ObjectManager objectManager,
            ModelEngine modelEngine,
            ModelRegistry modelRegistry
    ) {
        this.objectManager = objectManager;
        this.modelEngine = modelEngine;
        this.modelRegistry = modelRegistry;
    }

    @PostMapping("/import")
    public DispatchView importOrder(@Valid @RequestBody ImportDispatchRequest request) {
        ensureOilTerminalReady();
        String orderName = request.orderName() != null && !request.orderName().isBlank()
                ? request.orderName()
                : "dispatch" + request.orderNo();
        String orderPath = OilTerminalConstants.orderPath(orderName);
        if (objectManager.tree().findByPath(orderPath).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order exists: " + orderName);
        }

        var model = modelRegistry.requireByName(OilTerminalConstants.MODEL_DISPATCH);
        modelEngine.instantiateModel(model.id(), OilTerminalConstants.ORDERS, orderName, Map.of());

        OilTerminalObjects.setString(objectManager, orderPath, "orderNo", request.orderNo());
        OilTerminalObjects.setString(objectManager, orderPath, "productCode", request.productCode());
        OilTerminalObjects.setDouble(objectManager, orderPath, "plannedLiters", request.plannedLiters());
        OilTerminalObjects.setString(objectManager, orderPath, "vehiclePlate", request.vehiclePlate());
        OilTerminalObjects.setString(objectManager, orderPath, "status", DispatchStatus.PLANNED.wireValue());
        objectManager.persistNodeTree(orderPath);
        return toView(orderPath);
    }

    @GetMapping("/{orderName}")
    public DispatchView getOrder(@PathVariable String orderName) {
        String orderPath = OilTerminalConstants.orderPath(orderName);
        if (objectManager.tree().findByPath(orderPath).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + orderName);
        }
        return toView(orderPath);
    }

    private void ensureOilTerminalReady() {
        if (objectManager.tree().findByPath(OilTerminalConstants.ORDERS).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Oil terminal stand is not initialized");
        }
    }

    private DispatchView toView(String orderPath) {
        return new DispatchView(
                orderPath.substring(orderPath.lastIndexOf('.') + 1),
                orderPath,
                OilTerminalObjects.stringValue(objectManager, orderPath, "orderNo").orElse(""),
                OilTerminalObjects.stringValue(objectManager, orderPath, "status").orElse(""),
                OilTerminalObjects.doubleValue(objectManager, orderPath, "plannedLiters").orElse(0.0),
                OilTerminalObjects.doubleValue(objectManager, orderPath, "actualLiters").orElse(null),
                OilTerminalObjects.stringValue(objectManager, orderPath, "tankName").orElse(null),
                OilTerminalObjects.stringValue(objectManager, orderPath, "rackName").orElse(null),
                OilTerminalObjects.stringValue(objectManager, orderPath, "vehiclePlate").orElse(null)
        );
    }

    public record ImportDispatchRequest(
            @NotBlank String orderNo,
            @NotBlank String productCode,
            @NotNull Double plannedLiters,
            String vehiclePlate,
            String orderName
    ) {
    }

    public record DispatchView(
            String name,
            String path,
            String orderNo,
            String status,
            double plannedLiters,
            Double actualLiters,
            String tankName,
            String rackName,
            String vehiclePlate
    ) {
    }
}
