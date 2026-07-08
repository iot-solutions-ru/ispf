package com.ispf.server.platform.analytics;

import com.ispf.server.bootstrap.SystemObjectDescriptionReconciler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 28: asset analytics catalog and built-in derived-tag templates (BL-160).
 */
@Component
public class AssetAnalyticsBootstrap {

    private final AnalyticsBlueprintBootstrap analyticsBlueprintBootstrap;
    private final AssetAnalyticsService assetAnalyticsService;
    private final SystemObjectDescriptionReconciler systemObjectDescriptionReconciler;

    public AssetAnalyticsBootstrap(
            AnalyticsBlueprintBootstrap analyticsBlueprintBootstrap,
            AssetAnalyticsService assetAnalyticsService,
            SystemObjectDescriptionReconciler systemObjectDescriptionReconciler
    ) {
        this.analyticsBlueprintBootstrap = analyticsBlueprintBootstrap;
        this.assetAnalyticsService = assetAnalyticsService;
        this.systemObjectDescriptionReconciler = systemObjectDescriptionReconciler;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 18)
    @Transactional
    public void onReady() {
        analyticsBlueprintBootstrap.ensureAnalyticsModels();
        assetAnalyticsService.ensureCatalog();
        systemObjectDescriptionReconciler.reconcile();
    }
}
