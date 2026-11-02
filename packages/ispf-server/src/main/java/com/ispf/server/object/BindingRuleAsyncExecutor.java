package com.ispf.server.object;

import com.ispf.core.binding.BindingRule;
import com.ispf.server.concurrent.ElasticWorkerLauncher;
import com.ispf.server.config.BindingProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Async binding evaluation on an elastic worker pool.
 * Per {@code objectPath|ruleId} optionally coalesces bursts — only the latest pending evaluation
 * runs after the current one finishes when {@code ispf.binding.async-coalesce-enabled=true}.
 * Default is FIFO (coalesce off): every variable tick is evaluated by its binding rule.
 */
@Component
public class BindingRuleAsyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(BindingRuleAsyncExecutor.class);

    private final BindingProperties properties;
    private final ConcurrentHashMap<String, PerRuleRunner> runners = new ConcurrentHashMap<>();
    private final AtomicInteger pendingRules = new AtomicInteger();
    private final AtomicLong droppedSubmissions = new AtomicLong();
    private ElasticWorkerLauncher workers;

    public BindingRuleAsyncExecutor(BindingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void startWorkers() {
        ensureStarted();
    }

    private synchronized void ensureStarted() {
        if (workers != null) {
            return;
        }
        workers = new ElasticWorkerLauncher(
                properties.resolvedAsyncElastic(),
                pendingRules::get,
                "binding-async",
                this::drainOneBinding
        );
        workers.start();
    }

    public void schedule(String objectPath, BindingRule rule, Runnable evaluation) {
        ensureStarted();
        String key = objectPath + "|" + rule.id();
        runners.compute(key, (ignored, existing) -> {
            PerRuleRunner runner = existing != null ? existing : new PerRuleRunner(key);
            runner.submit(evaluation);
            return runner;
        });
        workers.signalWork();
    }

    public int runnerCount() {
        return runners.size();
    }

    public long droppedSubmissions() {
        return droppedSubmissions.get();
    }

    private boolean drainOneBinding() {
        for (PerRuleRunner runner : runners.values()) {
            if (runner.runOneEvaluation()) {
                return true;
            }
        }
        return false;
    }

    @PreDestroy
    public void shutdown() {
        if (workers != null) {
            workers.close();
        }
        runners.clear();
    }

    private final class PerRuleRunner {
        private final String key;
        private final AtomicReference<Runnable> pending = new AtomicReference<>();
        private final ConcurrentLinkedQueue<Runnable> fifo = new ConcurrentLinkedQueue<>();
        private final AtomicInteger fifoDepth = new AtomicInteger();
        private final AtomicBoolean pumpScheduled = new AtomicBoolean();

        private PerRuleRunner(String key) {
            this.key = key;
        }

        void submit(Runnable evaluation) {
            if (properties.isAsyncCoalesceEnabled()) {
                pending.set(evaluation);
            } else if (!offerFifo(evaluation)) {
                droppedSubmissions.incrementAndGet();
                return;
            }
            if (pumpScheduled.compareAndSet(false, true)) {
                pendingRules.incrementAndGet();
            }
        }

        boolean runOneEvaluation() {
            if (!pumpScheduled.get()) {
                return false;
            }
            Runnable task = properties.isAsyncCoalesceEnabled() ? pending.getAndSet(null) : pollFifo();
            if (task == null) {
                finishPumpIfIdle();
                return false;
            }
            try {
                task.run();
            } catch (Exception ex) {
                String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                log.error("Async binding {} failed: {}", key, detail);
            }
            boolean hasMore = hasQueuedWork();
            if (hasMore) {
                return true;
            }
            finishPumpIfIdle();
            return false;
        }

        private void finishPumpIfIdle() {
            pumpScheduled.set(false);
            boolean hasMore = hasQueuedWork();
            if (hasMore && pumpScheduled.compareAndSet(false, true)) {
                return;
            }
            pendingRules.decrementAndGet();
            runners.compute(key, (ignored, existing) -> {
                if (existing != this || pumpScheduled.get() || hasQueuedWork()) {
                    return existing;
                }
                return null;
            });
        }

        private boolean offerFifo(Runnable evaluation) {
            int capacity = properties.getAsyncFifoCapacity();
            while (true) {
                int depth = fifoDepth.get();
                if (depth >= capacity) {
                    return false;
                }
                if (fifoDepth.compareAndSet(depth, depth + 1)) {
                    fifo.offer(evaluation);
                    return true;
                }
            }
        }

        private Runnable pollFifo() {
            Runnable task = fifo.poll();
            if (task != null) {
                fifoDepth.decrementAndGet();
            }
            return task;
        }

        private boolean hasQueuedWork() {
            return properties.isAsyncCoalesceEnabled() ? pending.get() != null : fifoDepth.get() > 0;
        }
    }
}
