package com.ispf.server.spi;

import java.time.Duration;

/** Cluster leader lock used by the workflow retry scheduler. */
public interface LeaderLock {

    boolean tryAcquire(String lockName, Duration ttl);

    void release(String lockName);
}
