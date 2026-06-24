package com.ispf.server.object.bus;

import com.ispf.server.object.ObjectChangeEvent;

/**
 * Consumer of {@link ObjectChangeEvent} dispatched by {@link ObjectChangeEventBus}.
 */
public interface ObjectChangeAsyncHandler {

    void handle(ObjectChangeEvent event);

    default int order() {
        return 0;
    }
}
