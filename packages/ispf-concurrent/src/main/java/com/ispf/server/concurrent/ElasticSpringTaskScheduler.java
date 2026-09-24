package com.ispf.server.concurrent;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Spring {@link TaskScheduler} backed by {@link ElasticScheduledPool} for {@code @Scheduled} tasks.
 */
public final class ElasticSpringTaskScheduler implements TaskScheduler, DisposableBean {

    private final ElasticScheduledPool pool;
    private final ConcurrentTaskScheduler delegate;

    public ElasticSpringTaskScheduler(ElasticScheduledPool pool, ScheduledThreadPoolExecutor executor) {
        this.pool = pool;
        this.delegate = new ConcurrentTaskScheduler(executor);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        pool.signalLoad();
        return delegate.schedule(wrap(task), trigger);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Date startTime) {
        pool.signalLoad();
        return delegate.schedule(wrap(task), startTime);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        pool.signalLoad();
        return delegate.schedule(wrap(task), startTime);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Date startTime, long period) {
        pool.signalLoad();
        return delegate.scheduleAtFixedRate(wrap(task), startTime, period);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        pool.signalLoad();
        return delegate.scheduleAtFixedRate(wrap(task), startTime, period);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        pool.signalLoad();
        return delegate.scheduleAtFixedRate(wrap(task), period);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Date startTime, long delay) {
        pool.signalLoad();
        return delegate.scheduleWithFixedDelay(wrap(task), startTime, delay);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        pool.signalLoad();
        return delegate.scheduleWithFixedDelay(wrap(task), startTime, delay);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        pool.signalLoad();
        return delegate.scheduleWithFixedDelay(wrap(task), delay);
    }

    ElasticScheduledPool pool() {
        return pool;
    }

    private Runnable wrap(Runnable task) {
        return () -> {
            try {
                task.run();
            } finally {
                pool.signalIdle();
            }
        };
    }

    @Override
    public void destroy() {
        pool.close();
    }
}
