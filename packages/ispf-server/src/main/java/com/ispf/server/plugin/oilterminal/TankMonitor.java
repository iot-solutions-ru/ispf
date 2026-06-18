package com.ispf.server.plugin.oilterminal;

import com.ispf.plugin.oilterminal.DispatchStatus;
import com.ispf.plugin.oilterminal.OilTerminalConstants;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Links rack totalizer to dispatch order actualLiters and simulates filling on the stand.
 */
@Component
public class TankMonitor {

    private static final Logger log = LoggerFactory.getLogger(TankMonitor.class);

    private final ObjectManager objectManager;

    public TankMonitor(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @EventListener
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || event.variableName() == null) {
            return;
        }
        if (event.path().startsWith(OilTerminalConstants.RACKS + ".")
                && "totalizerL".equals(event.variableName())) {
            syncTotalizerToOrder(event.path());
        }
    }

    @Scheduled(fixedRate = 1000)
    public void simulateFilling() {
        for (var node : objectManager.tree().all()) {
            if (!OilTerminalObjects.isOilRack(node)) {
                continue;
            }
            String rackPath = node.path();
            boolean busy = OilTerminalObjects.booleanValue(objectManager, rackPath, "busy").orElse(false);
            if (!busy) {
                continue;
            }
            String orderName = OilTerminalObjects.stringValue(objectManager, rackPath, "currentOrderName").orElse("");
            if (orderName.isBlank()) {
                continue;
            }
            String orderPath = OilTerminalConstants.orderPath(orderName);
            if (objectManager.tree().findByPath(orderPath).isEmpty()) {
                continue;
            }
            DispatchStatus status = DispatchStatus.parse(
                    OilTerminalObjects.stringValue(objectManager, orderPath, "status").orElse("planned")
            );
            if (status != DispatchStatus.FILLING) {
                continue;
            }

            double flowRate = OilTerminalObjects.doubleValue(objectManager, rackPath, "flowRateLpm").orElse(800.0);
            double totalizer = OilTerminalObjects.doubleValue(objectManager, rackPath, "totalizerL").orElse(0.0);
            double planned = OilTerminalObjects.doubleValue(objectManager, orderPath, "plannedLiters").orElse(0.0);
            double next = Math.min(planned, totalizer + flowRate / 60.0);
            if (next <= totalizer) {
                continue;
            }
            OilTerminalObjects.setDouble(objectManager, rackPath, "totalizerL", next);
            OilTerminalObjects.setDouble(objectManager, orderPath, "actualLiters", next);
            objectManager.persistNodeTree(rackPath);
            objectManager.persistNodeTree(orderPath);
        }
    }

    private void syncTotalizerToOrder(String rackPath) {
        String orderName = OilTerminalObjects.stringValue(objectManager, rackPath, "currentOrderName").orElse("");
        if (orderName.isBlank()) {
            return;
        }
        String orderPath = OilTerminalConstants.orderPath(orderName);
        if (objectManager.tree().findByPath(orderPath).isEmpty()) {
            return;
        }
        OilTerminalObjects.doubleValue(objectManager, rackPath, "totalizerL").ifPresent(totalizer -> {
            OilTerminalObjects.setDouble(objectManager, orderPath, "actualLiters", totalizer);
            objectManager.persistNodeTree(orderPath);
            log.debug("Synced totalizer {} -> {}", rackPath, orderPath);
        });
    }
}
