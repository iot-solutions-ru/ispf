package com.ispf.server.object.bus;

import com.ispf.server.object.ObjectChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enqueues {@link ObjectChangeEvent} for async handlers after all synchronous listeners finish.
 */
@Component
public class ObjectChangeEventBusIngress {

    private final ObjectChangeEventBus eventBus;

    public ObjectChangeEventBusIngress(ObjectChangeEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void enqueue(ObjectChangeEvent event) {
        eventBus.submit(event);
    }
}
