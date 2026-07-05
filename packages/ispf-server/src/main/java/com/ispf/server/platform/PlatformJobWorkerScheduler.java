package com.ispf.server.platform;

import com.ispf.driver.ingress.ElasticWorkerScaler;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.report.ReportService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polls {@link PlatformJobService} and executes jobs on worker-capable replicas (ADR-0031).
 */
@Component
public class PlatformJobWorkerScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlatformJobWorkerScheduler.class);

    private final ClusterProperties clusterProperties;
    private final PlatformJobService jobService;
    private final ReportService reportService;
    private final AtomicInteger activeJobs = new AtomicInteger();
    private final ExecutorService executor;
    private ElasticWorkerScaler scaler;

    public PlatformJobWorkerScheduler(
            ClusterProperties clusterProperties,
            PlatformJobService jobService,
            ReportService reportService
    ) {
        this.clusterProperties = clusterProperties;
        this.jobService = jobService;
        this.reportService = reportService;
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "platform-job-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PostConstruct
    void initScaler() {
        if (clusterProperties.jobElasticEnabled()) {
            scaler = new ElasticWorkerScaler(
                    clusterProperties.resolvedJobMaxConcurrentMin(),
                    clusterProperties.resolvedJobMaxConcurrentMax(),
                    clusterProperties.jobElasticScaleUpThreshold(),
                    clusterProperties.jobElasticScaleDownSteps()
            );
        }
    }

    @Scheduled(fixedDelayString = "${ispf.cluster.job-poll-ms:2000}")
    public void poll() {
        if (!clusterProperties.isJobConsumerActive()) {
            return;
        }
        jobService.recoverExpiredRunningJobs();
        int max = resolvedMaxConcurrent();
        while (activeJobs.get() < max) {
            var claimed = jobService.claimNextJob();
            if (claimed.isEmpty()) {
                break;
            }
            activeJobs.incrementAndGet();
            PlatformJobService.ClaimedJob job = claimed.get();
            executor.submit(() -> runJob(job));
            if (clusterProperties.jobElasticEnabled() && activeJobs.get() >= clusterProperties.jobElasticScaleUpThreshold()) {
                max = resolvedMaxConcurrent();
            }
        }
    }

    private int resolvedMaxConcurrent() {
        if (!clusterProperties.jobElasticEnabled() || scaler == null) {
            return Math.max(1, clusterProperties.jobMaxConcurrent());
        }
        scaler.adjust(activeJobs.get());
        return scaler.targetWorkers();
    }

    private void runJob(PlatformJobService.ClaimedJob job) {
        UUID jobId = job.jobId();
        try {
            Map<String, Object> result = execute(job);
            jobService.completeJob(jobId, result);
        } catch (Exception ex) {
            log.warn("Platform job {} ({}) failed: {}", jobId, job.jobType(), ex.getMessage());
            jobService.failJob(jobId, ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        } finally {
            activeJobs.decrementAndGet();
        }
    }

    private Map<String, Object> execute(PlatformJobService.ClaimedJob job) {
        if (PlatformJobService.TYPE_REPORT_RUN.equals(job.jobType())) {
            String path = stringPayload(job.payload(), "path");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) job.payload().getOrDefault("parameters", Map.of());
            return reportService.run(path, parameters);
        }
        throw new IllegalArgumentException("Unsupported job type: " + job.jobType());
    }

    private static String stringPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Job payload missing " + key);
        }
        return String.valueOf(value);
    }
}
