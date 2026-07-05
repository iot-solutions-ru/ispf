package com.ispf.server.concurrent;

import com.ispf.driver.ingress.ElasticWorkerScaler;
import com.ispf.driver.ingress.IngressElasticSettings;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

/**
 * {@link ScheduledThreadPoolExecutor} whose core/max size tracks queue depth via {@link ElasticWorkerScaler}.
 */
public final class ElasticScheduledPool implements AutoCloseable {

    private final IngressElasticSettings settings;
    private final IntSupplier pendingCount;
    private final String threadNamePrefix;

    private ScheduledThreadPoolExecutor executor;
    private ElasticWorkerScaler scaler;
    private ScheduledExecutorService scaleScheduler;
    private ScheduledFuture<?> scaleTask;
    private final AtomicInteger threadIndex = new AtomicInteger();

    public ElasticScheduledPool(
            IngressElasticSettings settings,
            IntSupplier pendingCount,
            String threadNamePrefix
    ) {
        this.settings = settings;
        this.pendingCount = pendingCount;
        this.threadNamePrefix = threadNamePrefix;
    }

    public ScheduledThreadPoolExecutor start() {
        int minWorkers = settings.resolvedMinWorkers();
        int maxWorkers = settings.resolvedMaxWorkers();
        executor = new ScheduledThreadPoolExecutor(minWorkers, runnable -> {
            Thread thread = new Thread(runnable, threadNamePrefix + "-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executor.setMaximumPoolSize(maxWorkers);
        executor.setRemoveOnCancelPolicy(true);
        if (settings.enabled()) {
            scaler = new ElasticWorkerScaler(
                    minWorkers,
                    maxWorkers,
                    settings.scaleUpQueueThreshold(),
                    settings.scaleDownSteps()
            );
            scaleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, threadNamePrefix + "-scale");
                thread.setDaemon(true);
                return thread;
            });
            scaleTask = scaleScheduler.scheduleAtFixedRate(
                    this::adjustWorkers,
                    settings.scaleCheckIntervalMs(),
                    settings.scaleCheckIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
        }
        return executor;
    }

    public ScheduledThreadPoolExecutor executor() {
        return executor;
    }

    public void signalLoad() {
        if (settings.enabled() && pendingCount.getAsInt() >= settings.scaleUpQueueThreshold()) {
            adjustWorkers();
        }
    }

    public void adjustWorkers() {
        if (scaler == null || executor == null) {
            return;
        }
        scaler.adjust(pendingCount.getAsInt());
        int minWorkers = settings.resolvedMinWorkers();
        int maxWorkers = settings.resolvedMaxWorkers();
        int target = Math.min(maxWorkers, Math.max(minWorkers, scaler.targetWorkers()));
        executor.setMaximumPoolSize(target);
        executor.setCorePoolSize(target);
    }

    @Override
    public void close() {
        if (scaleTask != null) {
            scaleTask.cancel(false);
        }
        if (scaleScheduler != null) {
            scaleScheduler.shutdownNow();
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }
}
