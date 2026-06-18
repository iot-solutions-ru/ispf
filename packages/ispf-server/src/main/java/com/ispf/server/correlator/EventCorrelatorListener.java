package com.ispf.server.correlator;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class EventCorrelatorListener {

    private final EventCorrelatorService correlatorService;

    public EventCorrelatorListener(EventCorrelatorService correlatorService) {
        this.correlatorService = correlatorService;
    }

    @EventListener
    @Order(50)
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.EVENT_FIRED || event.variableName() == null) {
            return;
        }
        correlatorService.processEventFired(event.path(), event.variableName());
    }
}
