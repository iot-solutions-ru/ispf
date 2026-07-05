package com.ispf.server.object;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Keeps {@link BindingPeriodicScheduleRegistry} aligned when objects are deleted from the tree.
 */
@Component
public class BindingPeriodicScheduleListener {

    private final BindingPeriodicScheduleRegistry registry;
    private final BindingPeriodicScheduler scheduler;

    public BindingPeriodicScheduleListener(
            BindingPeriodicScheduleRegistry registry,
            BindingPeriodicScheduler scheduler
    ) {
        this.registry = registry;
        this.scheduler = scheduler;
    }

    @EventListener
    @Order(40)
    public void onObjectChange(ObjectChangeEvent event) {
        if (event.type() != ObjectChangeType.DELETED) {
            return;
        }
        registry.removeSubtree(event.path());
        scheduler.reschedule();
    }
}
