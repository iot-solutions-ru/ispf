package com.ispf.server.platform.analytics;

import com.ispf.server.bootstrap.SystemObjectDescriptionReconciler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers analytics MIXIN blueprint models used by historian binding rules (ADR-0041).
 * Tree catalog {@code root.platform.analytics} / {@code ANALYTICS_TEMPLATE} was removed per ADR-0041.
 */
@Component
public class AssetAnalyticsBootstrap {

    private final AnalyticsBlueprintBootstrap analyticsBlueprintBootstrap;
    private final SystemObjectDescriptionReconciler systemObjectDescriptionReconciler;

    public AssetAnalyticsBootstrap(
            AnalyticsBlueprintBootstrap analyticsBlueprintBootstrap,
            SystemObjectDescriptionReconciler systemObjectDescriptionReconciler
    ) {
        this.analyticsBlueprintBootstrap = analyticsBlueprintBootstrap;
        this.systemObjectDescriptionReconciler = systemObjectDescriptionReconciler;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 18)
    @Transactional
    public void onReady() {
        analyticsBlueprintBootstrap.ensureAnalyticsModels();
        systemObjectDescriptionReconciler.reconcile();
    }
}
