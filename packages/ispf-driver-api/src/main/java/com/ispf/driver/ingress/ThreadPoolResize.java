package com.ispf.driver.ingress;

import java.util.concurrent.ThreadPoolExecutor;

/** Safe elastic resize for {@link ThreadPoolExecutor} (core must not exceed max when shrinking). */
public final class ThreadPoolResize {

    private ThreadPoolResize() {
    }

    public static void apply(ThreadPoolExecutor pool, int maxWorkers, int targetCore) {
        int max = Math.max(1, maxWorkers);
        int core = Math.min(Math.max(1, targetCore), max);
        if (max < pool.getCorePoolSize()) {
            pool.setCorePoolSize(max);
        }
        pool.setMaximumPoolSize(max);
        pool.setCorePoolSize(core);
        while (pool.getPoolSize() < core && pool.getPoolSize() < pool.getMaximumPoolSize()) {
            pool.prestartCoreThread();
        }
    }
}
