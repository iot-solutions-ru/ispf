package com.ispf.server.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class AlertRuleBootstrap {

    private final AlertRuleService alertRuleService;
    private final boolean seedDemo;

    public AlertRuleBootstrap(
            AlertRuleService alertRuleService,
            @Value("${ispf.alert-rules.seed-demo:true}") boolean seedDemo
    ) {
        this.alertRuleService = alertRuleService;
        this.seedDemo = seedDemo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void seedDemoRules() {
        if (seedDemo) {
            alertRuleService.ensureDemoRules();
        }
    }
}
