package com.ispf.server.platform.analytics.frames;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.ObjectManager;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Opens and closes batch event frames when ISA-88 {@code batch-v1} phase changes (BL-208).
 */
@Component
public class EventFrameVariableChangeListener {

    private final ObjectManager objectManager;
    private final EventFrameService eventFrameService;

    public EventFrameVariableChangeListener(ObjectManager objectManager, EventFrameService eventFrameService) {
        this.objectManager = objectManager;
        this.eventFrameService = eventFrameService;
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE - 20)
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.replicaIngress()) {
            return;
        }
        if (event.type() != ObjectChangeType.VARIABLE_UPDATED || !"phase".equals(event.variableName())) {
            return;
        }
        PlatformObject node = objectManager.tree().findByPath(event.path()).orElse(null);
        if (node == null || node.type() != ObjectType.LOT) {
            return;
        }
        if (!isBatchLot(node)) {
            return;
        }
        String phase = EventFrameBlueprintBootstrap.readString(node, "phase");
        eventFrameService.onBatchPhaseChanged(event.path(), phase, event.observedAt());
    }

    private static boolean isBatchLot(PlatformObject node) {
        for (String blueprintId : node.appliedBlueprintIds()) {
            if (blueprintId.toLowerCase().contains("batch-v1")) {
                return true;
            }
        }
        return node.getVariable("batchId").isPresent() && node.getVariable("phase").isPresent();
    }
}
