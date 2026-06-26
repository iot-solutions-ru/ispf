package com.ispf.server.object;

import com.ispf.core.binding.BindingRule;
import com.ispf.server.config.BindingProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Async binding evaluation on a shared thread pool.
 * Per {@code objectPath|ruleId} coalesces bursts — only the latest pending evaluation
 * runs after the current one finishes (ordering preserved per rule).
 */
@Component
public class BindingRuleAsyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(BindingRuleAsyncExecutor.class);

    private final ExecutorService pool;
    private final ConcurrentHashMap<String, PerRuleRunner> runners = new ConcurrentHashMap<>();

    public BindingRuleAsyncExecutor(BindingProperties properties) {
        int threads = properties.getAsyncThreads();
        AtomicInteger threadIndex = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "binding-async-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public void schedule(String objectPath, BindingRule rule, Runnable evaluation) {
        String key = objectPath + "|" + rule.id();
        runners.computeIfAbsent(key, ignored -> new PerRuleRunner(key, pool))
                .submit(evaluation);
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
        runners.clear();
    }

    private static final class PerRuleRunner {
        private final String key;
        private final ExecutorService pool;
        private final AtomicReference<Runnable> pending = new AtomicReference<>();
        private final AtomicBoolean pumpScheduled = new AtomicBoolean();

        private PerRuleRunner(String key, ExecutorService pool) {
            this.key = key;
            this.pool = pool;
        }

        void submit(Runnable evaluation) {
            pending.set(evaluation);
            if (pumpScheduled.compareAndSet(false, true)) {
                pool.execute(this::pump);
            }
        }

        private void pump() {
            try {
                while (true) {
                    Runnable task = pending.getAndSet(null);
                    if (task == null) {
                        break;
                    }
                    try {
                        task.run();
                    } catch (Exception ex) {
                        log.warn("Async binding {} failed: {}", key, ex.getMessage());
                    }
                }
            } finally {
                pumpScheduled.set(false);
                if (pending.get() != null && pumpScheduled.compareAndSet(false, true)) {
                    pool.execute(this::pump);
                }
            }
        }
    }
}
