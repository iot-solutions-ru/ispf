package com.ispf.server.correlator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class EventCorrelatorBootstrap {

    private final EventCorrelatorService correlatorService;
    private final boolean seedDemo;

    public EventCorrelatorBootstrap(
            EventCorrelatorService correlatorService,
            @Value("${ispf.correlators.seed-demo:true}") boolean seedDemo
    ) {
        this.correlatorService = correlatorService;
        this.seedDemo = seedDemo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(101)
    public void seedDemoCorrelators() {
        if (seedDemo) {
            correlatorService.ensureDemoCorrelators();
        }
    }
}
