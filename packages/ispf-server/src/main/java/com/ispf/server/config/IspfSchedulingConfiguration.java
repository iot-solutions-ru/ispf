package com.ispf.server.config;

import com.ispf.server.concurrent.ElasticScheduledPool;
import com.ispf.server.concurrent.ElasticSpringTaskScheduler;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Elastic {@code @Scheduled} pool — avoids single-thread starvation and fixed oversized pools.
 */
@Configuration
public class IspfSchedulingConfiguration implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(IspfSchedulingConfiguration.class);

    private final ClusterProperties clusterProperties;
    private ElasticSpringTaskScheduler taskScheduler;

    public IspfSchedulingConfiguration(ClusterProperties clusterProperties) {
        this.clusterProperties = clusterProperties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ScheduledThreadPoolExecutor[] executorRef = new ScheduledThreadPoolExecutor[1];
        ElasticScheduledPool pool = new ElasticScheduledPool(
                clusterProperties.resolvedScheduledPoolElastic(),
                () -> executorRef[0] != null ? executorRef[0].getQueue().size() : 0,
                "ispf-scheduled"
        );
        executorRef[0] = pool.start();
        taskScheduler = new ElasticSpringTaskScheduler(pool, executorRef[0]);
        taskRegistrar.setTaskScheduler(taskScheduler);
        var elastic = clusterProperties.resolvedScheduledPoolElastic();
        log.info(
                "Spring @Scheduled pool started (threads={}-{}, elastic={}, scaleUpQueue={})",
                elastic.resolvedMinWorkers(),
                elastic.resolvedMaxWorkers(),
                elastic.enabled(),
                elastic.scaleUpQueueThreshold()
        );
    }

    @PreDestroy
    void shutdown() {
        if (taskScheduler != null) {
            taskScheduler.destroy();
        }
    }
}
