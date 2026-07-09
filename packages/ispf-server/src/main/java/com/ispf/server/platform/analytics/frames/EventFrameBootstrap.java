package com.ispf.server.platform.analytics.frames;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventFrameBootstrap {

    private final EventFrameBlueprintBootstrap blueprintBootstrap;

    public EventFrameBootstrap(EventFrameBlueprintBootstrap blueprintBootstrap) {
        this.blueprintBootstrap = blueprintBootstrap;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 19)
    @Transactional
    public void onReady() {
        blueprintBootstrap.ensureEventFrameModels();
    }
}
