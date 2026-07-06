package com.ispf.server.automation;

import com.ispf.server.config.BootstrapProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class AutomationBootstrap {

    private final AutomationTreeService automationTreeService;
    private final BootstrapProperties bootstrapProperties;

    public AutomationBootstrap(
            AutomationTreeService automationTreeService,
            BootstrapProperties bootstrapProperties
    ) {
        this.automationTreeService = automationTreeService;
        this.bootstrapProperties = bootstrapProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(90)
    public void initializeAutomationTree() {
        automationTreeService.migrateLegacyTables();
        if (bootstrapProperties.shouldSeedGeneralReferenceDemos()) {
            automationTreeService.ensureDemoAlertRule();
            automationTreeService.ensureDemoCorrelators();
        }
    }
}
