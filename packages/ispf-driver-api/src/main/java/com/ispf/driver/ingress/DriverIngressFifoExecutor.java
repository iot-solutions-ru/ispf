package com.ispf.driver.ingress;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Elastic FIFO executor for driver ingress when last-value-wins coalesce is disabled (L0 MQTT journal path).
 */
public final class DriverIngressFifoExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final IngressElasticSettings elastic;
    private final ElasticWorkerScaler scaler;
    private final ScheduledExecutorService scaleScheduler;
    private final ScheduledFuture<?> scaleTask;

    public DriverIngressFifoExecutor(
            IngressElasticSettings elastic,
            int queueCapacity,
            String threadNamePrefix,
            RejectedExecutionHandler rejectedHandler
    ) {
        this.elastic = elastic;
        int minWorkers = elastic.resolvedMinWorkers();
        int maxWorkers = elastic.resolvedMaxWorkers();
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
        );
        if (elastic.enabled()) {
            scaler = new ElasticWorkerScaler(
                    minWorkers,
                    maxWorkers,
                    elastic.scaleUpQueueThreshold(),
                    elastic.scaleDownSteps()
            );
            scaleScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, threadNamePrefix + "-scale");
                thread.setDaemon(true);
                return thread;
            });
            scaleTask = scaleScheduler.scheduleAtFixedRate(
                    this::adjustPool,
                    elastic.scaleCheckIntervalMs(),
                    elastic.scaleCheckIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
        } else {
            scaler = null;
            scaleScheduler = null;
            scaleTask = null;
        }
    }

    public void execute(Runnable task) {
        executor.execute(task);
        maybeScaleUp();
    }

    private void maybeScaleUp() {
        if (scaler != null && executor.getQueue().size() >= elastic.scaleUpQueueThreshold()) {
            adjustPool();
        }
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
        if (scaleTask != null) {
            scaleTask.cancel(false);
        }
        if (scaleScheduler != null) {
            scaleScheduler.shutdownNow();
        }
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
