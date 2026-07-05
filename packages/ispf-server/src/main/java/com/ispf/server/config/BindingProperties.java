package com.ispf.server.config;

import com.ispf.driver.ingress.IngressElasticSettings;
import com.ispf.server.binding.BindingAuditMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.binding")
public class BindingProperties {

    /** Fixed worker count when {@link #asyncElasticEnabled} is false. */
    private int asyncThreads = 16;

    private boolean asyncElasticEnabled = true;

    private int asyncThreadsMin = 4;

    private int asyncThreadsMax = 32;

    private int asyncScaleUpQueueThreshold = 50;

    private int asyncScaleDownSteps = 6;

    private int asyncScaleCheckIntervalMs = 200;

    /** When false, each async binding evaluation is queued (FIFO per rule). */
    private boolean asyncCoalesceEnabled = true;

    private Audit audit = new Audit();

    public static class Audit {
        /** Master kill switch — when false, no binding audit writes regardless of per-object flags. */
        private boolean enabled = false;
        /** {@code errors}, {@code changes}, or {@code all}. */
        private String mode = "changes";
        /** 0.0–1.0 sampling when enabled (1.0 = every matching record). */
        private double sampleRate = 1.0;
        private boolean asyncEnabled = true;
        private int queueCapacity = 10_000;
        private int batchSize = 100;
        private long flushIntervalMs = 200;
        private int retentionDays = 7;
        /** Fixed writer count when {@link #elasticWriterEnabled} is false. */
        private int writerThreads = 2;
        private boolean elasticWriterEnabled = true;
        private int writerThreadsMin = 2;
        private int writerThreadsMax = 16;
        private int elasticScaleUpQueueThreshold = 50;
        private int elasticScaleDownSteps = 6;
        private int elasticScaleCheckIntervalMs = 200;

        public boolean isElasticWriterEnabled() {
            return elasticWriterEnabled;
        }

        public void setElasticWriterEnabled(boolean elasticWriterEnabled) {
            this.elasticWriterEnabled = elasticWriterEnabled;
        }

        public int getWriterThreads() {
            return writerThreads;
        }

        public void setWriterThreads(int writerThreads) {
            this.writerThreads = Math.max(1, writerThreads);
        }

        public int getWriterThreadsMin() {
            return writerThreadsMin;
        }

        public void setWriterThreadsMin(int writerThreadsMin) {
            this.writerThreadsMin = Math.max(1, writerThreadsMin);
        }

        public int getWriterThreadsMax() {
            return writerThreadsMax;
        }

        public void setWriterThreadsMax(int writerThreadsMax) {
            this.writerThreadsMax = Math.max(1, writerThreadsMax);
        }

        public int getElasticScaleUpQueueThreshold() {
            return elasticScaleUpQueueThreshold;
        }

        public void setElasticScaleUpQueueThreshold(int elasticScaleUpQueueThreshold) {
            this.elasticScaleUpQueueThreshold = Math.max(1, elasticScaleUpQueueThreshold);
        }

        public int getElasticScaleDownSteps() {
            return elasticScaleDownSteps;
        }

        public void setElasticScaleDownSteps(int elasticScaleDownSteps) {
            this.elasticScaleDownSteps = Math.max(1, elasticScaleDownSteps);
        }

        public int getElasticScaleCheckIntervalMs() {
            return elasticScaleCheckIntervalMs;
        }

        public void setElasticScaleCheckIntervalMs(int elasticScaleCheckIntervalMs) {
            this.elasticScaleCheckIntervalMs = Math.max(50, elasticScaleCheckIntervalMs);
        }

        public com.ispf.driver.ingress.IngressElasticSettings resolvedElastic() {
            return new com.ispf.driver.ingress.IngressElasticSettings(
                    elasticWriterEnabled,
                    elasticWriterEnabled ? writerThreadsMin : writerThreads,
                    elasticWriterEnabled ? writerThreadsMax : writerThreads,
                    elasticScaleUpQueueThreshold,
                    elasticScaleDownSteps,
                    elasticScaleCheckIntervalMs
            );
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public BindingAuditMode resolvedMode() {
            return BindingAuditMode.parse(mode);
        }

        public double getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
        }

        public boolean isAsyncEnabled() {
            return asyncEnabled;
        }

        public void setAsyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getFlushIntervalMs() {
            return flushIntervalMs;
        }

        public void setFlushIntervalMs(long flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }
    }

    public int getAsyncThreads() {
        return asyncThreads;
    }

    public void setAsyncThreads(int asyncThreads) {
        this.asyncThreads = Math.max(1, asyncThreads);
    }

    public boolean isAsyncElasticEnabled() {
        return asyncElasticEnabled;
    }

    public void setAsyncElasticEnabled(boolean asyncElasticEnabled) {
        this.asyncElasticEnabled = asyncElasticEnabled;
    }

    public int getAsyncThreadsMin() {
        return asyncThreadsMin;
    }

    public void setAsyncThreadsMin(int asyncThreadsMin) {
        this.asyncThreadsMin = Math.max(1, asyncThreadsMin);
    }

    public int getAsyncThreadsMax() {
        return asyncThreadsMax;
    }

    public void setAsyncThreadsMax(int asyncThreadsMax) {
        this.asyncThreadsMax = Math.max(1, asyncThreadsMax);
    }

    public int getAsyncScaleUpQueueThreshold() {
        return asyncScaleUpQueueThreshold;
    }

    public void setAsyncScaleUpQueueThreshold(int asyncScaleUpQueueThreshold) {
        this.asyncScaleUpQueueThreshold = Math.max(1, asyncScaleUpQueueThreshold);
    }

    public int getAsyncScaleDownSteps() {
        return asyncScaleDownSteps;
    }

    public void setAsyncScaleDownSteps(int asyncScaleDownSteps) {
        this.asyncScaleDownSteps = Math.max(1, asyncScaleDownSteps);
    }

    public int getAsyncScaleCheckIntervalMs() {
        return asyncScaleCheckIntervalMs;
    }

    public void setAsyncScaleCheckIntervalMs(int asyncScaleCheckIntervalMs) {
        this.asyncScaleCheckIntervalMs = Math.max(50, asyncScaleCheckIntervalMs);
    }

    public boolean isAsyncCoalesceEnabled() {
        return asyncCoalesceEnabled;
    }

    public void setAsyncCoalesceEnabled(boolean asyncCoalesceEnabled) {
        this.asyncCoalesceEnabled = asyncCoalesceEnabled;
    }

    public int resolvedAsyncThreadsMin() {
        return asyncElasticEnabled ? asyncThreadsMin : asyncThreads;
    }

    public int resolvedAsyncThreadsMax() {
        return asyncElasticEnabled ? asyncThreadsMax : asyncThreads;
    }

    public IngressElasticSettings resolvedAsyncElastic() {
        return new IngressElasticSettings(
                asyncElasticEnabled,
                resolvedAsyncThreadsMin(),
                resolvedAsyncThreadsMax(),
                asyncScaleUpQueueThreshold,
                asyncScaleDownSteps,
                asyncScaleCheckIntervalMs
        );
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit != null ? audit : new Audit();
    }
}
