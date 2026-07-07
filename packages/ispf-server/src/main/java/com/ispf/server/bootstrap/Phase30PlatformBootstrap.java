package com.ispf.server.bootstrap;

import com.ispf.server.eventfilter.EventFilterObjectService;
import com.ispf.server.process.ProcessProgramObjectService;
import com.ispf.server.query.QueryDefinitionService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 30: queries engine skeleton and reusable event filter objects.
 */
@Component
public class Phase30PlatformBootstrap {

    private final Phase30BlueprintBootstrap phase30BlueprintBootstrap;
    private final QueryDefinitionService queryDefinitionService;
    private final EventFilterObjectService eventFilterObjectService;
    private final ProcessProgramObjectService processProgramObjectService;
    private final SystemObjectDescriptionReconciler systemObjectDescriptionReconciler;

    public Phase30PlatformBootstrap(
            Phase30BlueprintBootstrap phase30BlueprintBootstrap,
            QueryDefinitionService queryDefinitionService,
            EventFilterObjectService eventFilterObjectService,
            ProcessProgramObjectService processProgramObjectService,
            SystemObjectDescriptionReconciler systemObjectDescriptionReconciler
    ) {
        this.phase30BlueprintBootstrap = phase30BlueprintBootstrap;
        this.queryDefinitionService = queryDefinitionService;
        this.eventFilterObjectService = eventFilterObjectService;
        this.processProgramObjectService = processProgramObjectService;
        this.systemObjectDescriptionReconciler = systemObjectDescriptionReconciler;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 21)
    @Transactional
    public void onReady() {
        phase30BlueprintBootstrap.ensurePhase30Models();
        queryDefinitionService.ensureCatalog();
        eventFilterObjectService.ensureCatalog();
        processProgramObjectService.ensureCatalog();
        systemObjectDescriptionReconciler.reconcile();
    }
}
