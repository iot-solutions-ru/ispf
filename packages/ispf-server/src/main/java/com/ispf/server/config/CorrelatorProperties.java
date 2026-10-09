package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ispf.correlator")
public class CorrelatorProperties {

    /**
     * When true, matching correlators are evaluated on keyed single-thread lanes (hash of correlator id).
     * Disable for tests that expect inline processing on the publisher thread.
     */
    private boolean asyncDispatch = true;

    /** Number of single-thread evaluation lanes. */
    private int dispatchLanes = 8;

    /** Capacity of each evaluation lane queue. */
    private int dispatchQueueCapacity = 10_000;

    /**
     * When true, {@code SEND_WEBHOOK}/{@code SEND_EMAIL}/{@code SEND_SMS} run on a separate action pool
     * after the correlator transaction commits.
     */
    private boolean asyncActions = true;

    private int actionWorkers = 4;
    private int actionQueueCapacity = 2_000;

    public boolean isAsyncDispatch() {
        return asyncDispatch;
    }

    public void setAsyncDispatch(boolean asyncDispatch) {
        this.asyncDispatch = asyncDispatch;
    }

    public int getDispatchLanes() {
        return dispatchLanes;
    }

    public void setDispatchLanes(int dispatchLanes) {
        this.dispatchLanes = Math.max(1, dispatchLanes);
    }

    public int getDispatchQueueCapacity() {
        return dispatchQueueCapacity;
    }

    public void setDispatchQueueCapacity(int dispatchQueueCapacity) {
        this.dispatchQueueCapacity = Math.max(1, dispatchQueueCapacity);
    }

    public boolean isAsyncActions() {
        return asyncActions;
    }

    public void setAsyncActions(boolean asyncActions) {
        this.asyncActions = asyncActions;
    }

    public int getActionWorkers() {
        return actionWorkers;
    }

    public void setActionWorkers(int actionWorkers) {
        this.actionWorkers = Math.max(1, actionWorkers);
    }

    public int getActionQueueCapacity() {
        return actionQueueCapacity;
    }

    public void setActionQueueCapacity(int actionQueueCapacity) {
        this.actionQueueCapacity = Math.max(1, actionQueueCapacity);
    }
}
