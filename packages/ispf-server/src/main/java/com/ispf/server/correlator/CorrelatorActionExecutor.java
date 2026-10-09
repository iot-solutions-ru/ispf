package com.ispf.server.correlator;

import com.ispf.server.config.CorrelatorProperties;
import com.ispf.server.platform.AutomationMetricsRecorder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded pool for correlator side-effects that must not block evaluation lanes (HTTP notifications).
 */
@Component
public class CorrelatorActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(CorrelatorActionExecutor.class);

    private final ThreadPoolExecutor executor;
    private final AutomationMetricsRecorder metricsRecorder;

    public CorrelatorActionExecutor(CorrelatorProperties properties, AutomationMetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(properties.getActionQueueCapacity());
        this.executor = new ThreadPoolExecutor(
                properties.getActionWorkers(),
                properties.getActionWorkers(),
                60L,
                TimeUnit.SECONDS,
                queue,
                runnable -> {
                    Thread thread = new Thread(runnable, "correlator-action");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, pool) -> {
                    metricsRecorder.recordCorrelatorActionDropped();
                    log.warn("Correlator action queue full; running notification on caller thread");
                    runnable.run();
                }
        );
        metricsRecorder.bindCorrelatorActionQueue(queue);
    }

    public void execute(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ex) {
            metricsRecorder.recordCorrelatorActionDropped();
            log.warn("Correlator action rejected; running on caller thread");
            task.run();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
