package com.ispf.server.application.bundle;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class BundleVisualGroupStartupSync {

    private final BundleVisualGroupService bundleVisualGroupService;

    public BundleVisualGroupStartupSync(BundleVisualGroupService bundleVisualGroupService) {
        this.bundleVisualGroupService = bundleVisualGroupService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 10)
    public void syncBundleVisualGroups() {
        bundleVisualGroupService.syncAllRegisteredBundles();
    }
}
