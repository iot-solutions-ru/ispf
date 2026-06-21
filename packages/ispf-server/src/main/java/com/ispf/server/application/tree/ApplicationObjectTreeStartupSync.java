package com.ispf.server.application.tree;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class ApplicationObjectTreeStartupSync {

    private final ApplicationObjectTreeService treeService;

    public ApplicationObjectTreeStartupSync(ApplicationObjectTreeService treeService) {
        this.treeService = treeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void syncApplicationsIntoObjectTree() {
        // Phase 14: legacy application mirror tree disabled — runtime uses platform object catalogs.
    }
}
