package com.ispf.server.spi;

import java.time.Duration;

/**
 * Cluster-wide lease for singleton jobs: one replica at a time runs them.
 * <p>
 * {@link #runIfLeader} leases are sticky. The holder renews them in the background while the task runs and
 * while ticks keep coming within {@code ttl}, so leadership moves only when the holder stops ticking, shuts
 * down, or dies.
 */
public interface LeaderLock {

    /**
     * Runs {@code task} if this replica holds the lease or can take it.
     *
     * @return false when another replica holds the lease and the task did not run
     */
    boolean runIfLeader(String lockName, Duration ttl, Runnable task);

    /**
     * Whether this replica still holds a live {@link #runIfLeader} lease, judged on its own monotonic clock.
     * Long loops check it between units of work so a holder that lost the lease (pause, database outage)
     * stops instead of overlapping with the new leader.
     */
    boolean isHeld(String lockName);

    /** Acquires or renews a lease that lasts {@code ttl}; it is not renewed in the background. */
    boolean tryAcquire(String lockName, Duration ttl);

    void release(String lockName);
}
