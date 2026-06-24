package com.ispf.server.correlator;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeAsyncHandler;
import org.springframework.stereotype.Component;

@Component
public class EventCorrelatorListener implements ObjectChangeAsyncHandler {

    private final EventCorrelatorService correlatorService;

    public EventCorrelatorListener(EventCorrelatorService correlatorService) {
        this.correlatorService = correlatorService;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void handle(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.EVENT_FIRED || event.variableName() == null) {
            return;
        }
        correlatorService.processEventFired(event.path(), event.variableName());
    }
}
