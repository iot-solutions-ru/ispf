package com.ispf.server.application.reference.mes;

import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.bootstrap.SystemObjectDescriptionReconciler;
import com.ispf.server.object.ObjectManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures first-class MES catalog folders under {@code root.platform.mes.*} (BL-164).
 */
@Component
public class MesPlatformBootstrap {

    private final ObjectManager objectManager;
    private final SystemObjectDescriptionReconciler systemObjectDescriptionReconciler;
    private final MesBlueprintBootstrap mesBlueprintBootstrap;

    public MesPlatformBootstrap(
            ObjectManager objectManager,
            SystemObjectDescriptionReconciler systemObjectDescriptionReconciler,
            MesBlueprintBootstrap mesBlueprintBootstrap
    ) {
        this.objectManager = objectManager;
        this.systemObjectDescriptionReconciler = systemObjectDescriptionReconciler;
        this.mesBlueprintBootstrap = mesBlueprintBootstrap;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 19)
    @Transactional
    public void onReady() {
        ensureCatalog();
        systemObjectDescriptionReconciler.reconcile();
    }

    public void ensureCatalog() {
        SystemObjectCatalogSupport.ensureFolder(objectManager, MesPaths.MES_ROOT, ObjectType.MES, null);
        SystemObjectCatalogSupport.ensureFolder(objectManager, MesPaths.WORK_ORDERS, ObjectType.WORK_ORDERS, null);
        SystemObjectCatalogSupport.ensureFolder(objectManager, MesPaths.OPERATIONS, ObjectType.OPERATIONS, null);
        SystemObjectCatalogSupport.ensureFolder(objectManager, MesPaths.LOTS, ObjectType.LOTS, null);
        SystemObjectCatalogSupport.ensureFolder(objectManager, MesPaths.SHIFTS, ObjectType.SHIFTS, null);
        SystemObjectCatalogSupport.ensureFolder(objectManager, MesPaths.QUALITY_RECORDS, ObjectType.QUALITY_RECORDS, null);
        SystemObjectCatalogSupport.ensureFolder(objectManager, MesPaths.INSTANCES, ObjectType.MES_INSTANCES, null);
        mesBlueprintBootstrap.ensureMesModels();
    }
}
