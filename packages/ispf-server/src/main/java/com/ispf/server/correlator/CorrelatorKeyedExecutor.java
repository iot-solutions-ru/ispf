package com.ispf.server.correlator;

import com.ispf.server.config.CorrelatorProperties;
import com.ispf.server.platform.AutomationMetricsRecorder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Hash-partitioned single-thread lanes: one correlator id always runs on the same lane,
 * so window/cooldown state updates stay ordered without per-correlator locks.
 */
@Component
public class CorrelatorKeyedExecutor {

    private static final Logger log = LoggerFactory.getLogger(CorrelatorKeyedExecutor.class);

    private final ExecutorService[] lanes;
    private final LinkedBlockingQueue<Runnable>[] queues;
    private final AutomationMetricsRecorder metricsRecorder;

    @SuppressWarnings("unchecked")
    public CorrelatorKeyedExecutor(CorrelatorProperties properties, AutomationMetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
        int laneCount = properties.getDispatchLanes();
        this.lanes = new ExecutorService[laneCount];
        this.queues = new LinkedBlockingQueue[laneCount];
        for (int i = 0; i < laneCount; i++) {
            LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(properties.getDispatchQueueCapacity());
            queues[i] = queue;
            int lane = i;
            lanes[i] = new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    queue,
                    runnable -> {
                        Thread thread = new Thread(runnable, "correlator-eval-" + lane);
                        thread.setDaemon(true);
                        return thread;
                    },
                    (runnable, executor) -> {
                        metricsRecorder.recordCorrelatorDispatchDropped();
                        log.warn("Correlator evaluation lane {} full; running on caller thread", lane);
                        runnable.run();
                    }
            );
            metricsRecorder.bindCorrelatorDispatchQueue(lane, queue);
        }
    }

    public void execute(String correlatorId, Runnable task) {
        int lane = Math.floorMod(correlatorId.hashCode(), lanes.length);
        try {
            lanes[lane].execute(task);
        } catch (RejectedExecutionException ex) {
            metricsRecorder.recordCorrelatorDispatchDropped();
            log.warn("Correlator evaluation rejected for {}; running on caller thread", correlatorId);
            task.run();
        }
    }

    @PreDestroy
    void shutdown() {
        for (ExecutorService lane : lanes) {
            lane.shutdownNow();
        }
    }
}
