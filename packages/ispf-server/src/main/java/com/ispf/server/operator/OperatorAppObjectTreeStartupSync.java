package com.ispf.server.operator;

import com.ispf.server.object.PlatformObjectReadinessGate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class OperatorAppObjectTreeStartupSync {

    private final OperatorAppObjectTreeService treeService;

    public OperatorAppObjectTreeStartupSync(OperatorAppObjectTreeService treeService) {
        this.treeService = treeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(PlatformObjectReadinessGate.AFTER_OBJECT_TREE_READY_ORDER)
    public void syncOperatorAppsIntoObjectTree() {
        treeService.syncAll();
    }
}
