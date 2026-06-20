package com.ispf.server.automation;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class AutomationBootstrap {

    private final AutomationTreeService automationTreeService;

    public AutomationBootstrap(AutomationTreeService automationTreeService) {
        this.automationTreeService = automationTreeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(90)
    public void initializeAutomationTree() {
        automationTreeService.migrateLegacyTables();
        automationTreeService.ensureDemoAlertRule();
        automationTreeService.ensureDemoCorrelators();
    }
}
