package com.ispf.driver.ingress;

/**
 * Computes target worker count from queue depth for elastic async lanes.
 * <p>
 * Event-driven contract: call {@link #adjust(int)} when work is enqueued (scale-up) and when a
 * worker finds the queue empty (scale-down via consecutive empty observations). No periodic polling.
 */
public final class ElasticWorkerScaler {

    private int minWorkers;
    private int maxWorkers;
    private int scaleUpQueueThreshold;
    private int scaleDownSteps;
    private int targetWorkers;
    private int consecutiveEmptyChecks;

    public ElasticWorkerScaler(int minWorkers, int maxWorkers, int scaleUpQueueThreshold, int scaleDownSteps) {
        if (minWorkers < 1) {
            throw new IllegalArgumentException("minWorkers must be >= 1");
        }
        if (maxWorkers < minWorkers) {
            throw new IllegalArgumentException("maxWorkers must be >= 1 and >= minWorkers");
        }
        if (scaleUpQueueThreshold < 1) {
            throw new IllegalArgumentException("scaleUpQueueThreshold must be >= 1");
        }
        if (scaleDownSteps < 1) {
            throw new IllegalArgumentException("scaleDownSteps must be >= 1");
        }
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
        this.scaleUpQueueThreshold = scaleUpQueueThreshold;
        this.scaleDownSteps = scaleDownSteps;
        this.targetWorkers = minWorkers;
    }

    public int targetWorkers() {
        return targetWorkers;
    }

    /**
     * @param queueSize current pending work units
     * @return true when target worker count changed
     */
    public boolean adjust(int queueSize) {
        int previous = targetWorkers;
        if (queueSize >= scaleUpQueueThreshold) {
            consecutiveEmptyChecks = 0;
            int desired = minWorkers + (queueSize / scaleUpQueueThreshold);
            targetWorkers = Math.min(maxWorkers, Math.max(targetWorkers, desired));
        } else if (queueSize == 0) {
            consecutiveEmptyChecks++;
            if (consecutiveEmptyChecks >= scaleDownSteps && targetWorkers > minWorkers) {
                targetWorkers--;
                consecutiveEmptyChecks = 0;
            }
        } else {
            consecutiveEmptyChecks = 0;
        }
        return targetWorkers != previous;
    }

    public void reconfigure(int minWorkers, int maxWorkers, int scaleUpQueueThreshold, int scaleDownSteps) {
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
        this.scaleUpQueueThreshold = scaleUpQueueThreshold;
        this.scaleDownSteps = scaleDownSteps;
        targetWorkers = Math.min(maxWorkers, Math.max(minWorkers, targetWorkers));
    }
}
