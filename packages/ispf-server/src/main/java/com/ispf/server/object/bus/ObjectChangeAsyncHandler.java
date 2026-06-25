package com.ispf.server.object.bus;

import com.ispf.server.object.ObjectChangeEvent;

/**
 * Consumer of {@link ObjectChangeEvent} dispatched by {@link ObjectChangeEventBus}.
 *
 * <p>When {@link com.ispf.server.config.ObjectChangeProperties#isSplitLanesEnabled()} is true,
 * handlers are routed to the lane returned by {@link #lane()}. Telemetry events
 * ({@code event.telemetry()==true}) are enqueued on the telemetry lane; events with
 * {@code event.automationEligible()==true} are also enqueued on the automation lane.
 */
public interface ObjectChangeAsyncHandler {

    void handle(ObjectChangeEvent event);

    default int order() {
        return 0;
    }

    default ObjectChangeLane lane() {
        return ObjectChangeLane.AUTOMATION;
    }
}
