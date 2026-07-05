package com.ispf.server.concurrent;

import com.ispf.driver.ingress.ElasticWorkerScaler;
import com.ispf.driver.ingress.IngressElasticSettings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntSupplier;

/**
 * Cached worker pool with optional {@link ElasticWorkerScaler} ramp (L3 gateway dispatch, binding async, driver I/O).
 */
public final class ElasticWorkerLauncher implements AutoCloseable {

    @FunctionalInterface
    public interface WorkerTask {
        /** @return true when work was processed and the worker should loop immediately */
        boolean runOnce();
    }

    private final IngressElasticSettings settings;
    private final IntSupplier queueSize;
    private final String workerThreadName;
    private final WorkerTask workerTask;

    private volatile boolean running;
    private ExecutorService workers;
    private ElasticWorkerScaler scaler;
    private ScheduledExecutorService scaleScheduler;
    private ScheduledFuture<?> scaleTask;
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private volatile Thread drainWaiter;

    public ElasticWorkerLauncher(
            IngressElasticSettings settings,
            IntSupplier queueSize,
            String workerThreadName,
            WorkerTask workerTask
    ) {
        this.settings = settings;
        this.queueSize = queueSize;
        this.workerThreadName = workerThreadName;
        this.workerTask = workerTask;
    }

    public void start() {
        running = true;
        if (settings.enabled()) {
            scaler = new ElasticWorkerScaler(
                    settings.resolvedMinWorkers(),
                    settings.resolvedMaxWorkers(),
                    settings.scaleUpQueueThreshold(),
                    settings.scaleDownSteps()
            );
            scaleScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, workerThreadName + "-scale");
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
        workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, workerThreadName);
            thread.setDaemon(true);
            return thread;
        });
        int initial = settings.enabled() ? settings.resolvedMinWorkers() : settings.resolvedMaxWorkers();
        for (int i = 0; i < initial; i++) {
            spawnWorker();
        }
    }

    public AtomicInteger activeWorkers() {
        return activeWorkers;
    }

    public void signalWork() {
        if (settings.enabled() && queueSize.getAsInt() >= settings.scaleUpQueueThreshold()) {
            adjustWorkers();
        }
        LockSupport.unpark(drainWaiter);
    }

    public boolean isRunning() {
        return running;
    }

    public boolean shouldWorkerContinue() {
        return running || queueSize.getAsInt() > 0;
    }

    public boolean shouldWorkerExitIdle() {
        return scaler != null && activeWorkers.get() > scaler.targetWorkers() && queueSize.getAsInt() == 0;
    }

    public void parkWorker() {
        drainWaiter = Thread.currentThread();
        LockSupport.parkNanos(250_000L);
    }

    public void adjustWorkers() {
        if (scaler == null || !running) {
            return;
        }
        scaler.adjust(queueSize.getAsInt());
        while (activeWorkers.get() < scaler.targetWorkers()
                && activeWorkers.get() < settings.resolvedMaxWorkers()) {
            spawnWorker();
        }
    }

    private void spawnWorker() {
        if (workers == null || activeWorkers.get() >= settings.resolvedMaxWorkers()) {
            return;
        }
        workers.submit(this::workerLoop);
    }

    private void workerLoop() {
        activeWorkers.incrementAndGet();
        try {
            while (shouldWorkerContinue()) {
                if (shouldWorkerExitIdle()) {
                    break;
                }
                if (workerTask.runOnce()) {
                    continue;
                }
                parkWorker();
            }
        } finally {
            activeWorkers.decrementAndGet();
        }
    }

    @Override
    public void close() {
        running = false;
        if (scaleTask != null) {
            scaleTask.cancel(false);
        }
        if (scaleScheduler != null) {
            scaleScheduler.shutdownNow();
        }
        LockSupport.unpark(drainWaiter);
        if (workers != null) {
            workers.shutdownNow();
        }
    }
}
