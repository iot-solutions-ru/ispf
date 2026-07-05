package com.ispf.driver.ingress;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Elastic FIFO executor for driver ingress when last-value-wins coalesce is disabled (L0 MQTT journal path).
 * Pool size tracks queue depth on enqueue and after each task — no periodic scale timer.
 */
public final class DriverIngressFifoExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final IngressElasticSettings elastic;
    private final ElasticWorkerScaler scaler;

    public DriverIngressFifoExecutor(
            IngressElasticSettings elastic,
            int queueCapacity,
            String threadNamePrefix,
            RejectedExecutionHandler rejectedHandler
    ) {
        this.elastic = elastic;
        int minWorkers = elastic.resolvedMinWorkers();
        int maxWorkers = elastic.resolvedMaxWorkers();
        ElasticWorkerScaler createdScaler = null;
        if (elastic.enabled()) {
            createdScaler = new ElasticWorkerScaler(
                    minWorkers,
                    maxWorkers,
                    elastic.scaleUpQueueThreshold(),
                    elastic.scaleDownSteps()
            );
        }
        this.scaler = createdScaler;
        executor = new ThreadPoolExecutor(
                minWorkers,
                maxWorkers,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(1, queueCapacity)),
                runnable -> {
                    Thread thread = new Thread(runnable, threadNamePrefix);
                    thread.setDaemon(true);
                    return thread;
                },
                rejectedHandler
        ) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                adjustPool();
            }
        };
    }

    public void execute(Runnable task) {
        executor.execute(task);
        adjustPool();
    }

    private void adjustPool() {
        if (scaler == null) {
            return;
        }
        scaler.adjust(executor.getQueue().size());
        int target = scaler.targetWorkers();
        if (target > executor.getCorePoolSize()) {
            executor.setCorePoolSize(target);
        }
        while (executor.getPoolSize() < target && executor.getPoolSize() < executor.getMaximumPoolSize()) {
            executor.prestartCoreThread();
        }
        if (target < executor.getCorePoolSize()) {
            executor.setCorePoolSize(target);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
