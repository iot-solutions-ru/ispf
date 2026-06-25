package com.ispf.server.object.bus;

/**
 * Computes target worker count from queue depth for elastic object-change lanes.
 */
final class ObjectChangeWorkerScaler {

    private int minWorkers;
    private int maxWorkers;
    private int scaleUpQueueThreshold;
    private int scaleDownSteps;
    private int targetWorkers;
    private int consecutiveEmptyChecks;

    ObjectChangeWorkerScaler(int minWorkers, int maxWorkers, int scaleUpQueueThreshold, int scaleDownSteps) {
        if (minWorkers < 1) {
            throw new IllegalArgumentException("minWorkers must be >= 1");
        }
        if (maxWorkers < minWorkers) {
            throw new IllegalArgumentException("maxWorkers must be >= minWorkers");
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

    int targetWorkers() {
        return targetWorkers;
    }

    /**
     * @param queueSize current pending events
     * @return true when target worker count changed
     */
    boolean adjust(int queueSize) {
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

    void reconfigure(int minWorkers, int maxWorkers, int scaleUpQueueThreshold, int scaleDownSteps) {
        this.minWorkers = minWorkers;
        this.maxWorkers = maxWorkers;
        this.scaleUpQueueThreshold = scaleUpQueueThreshold;
        this.scaleDownSteps = scaleDownSteps;
        targetWorkers = Math.min(maxWorkers, Math.max(minWorkers, targetWorkers));
    }
}
