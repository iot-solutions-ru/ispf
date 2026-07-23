package com.ispf.server.config;

import com.ispf.server.application.function.FunctionAuditMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.function")
public class FunctionProperties {

    private Java java = new Java();
    private Audit audit = new Audit();

    /**
     * In-process Java function compile/execute. Default {@code true} for local/test/dev
     * (admin-authored trusted code); prod profile defaults {@code false} — see
     * {@code application-prod.yml}. Regex denylist is not a process sandbox — ADR-0045.
     */
    public static class Java {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Audit {
        /** Master kill switch — when false, no function audit writes regardless of per-object flags. */
        private boolean enabled = false;
        /** {@code errors} or {@code all}. */
        private String mode = "errors";
        /** 0.0–1.0 sampling when enabled (1.0 = every matching record). */
        private double sampleRate = 1.0;
        private boolean asyncEnabled = true;
        private int queueCapacity = 10_000;
        private int batchSize = 100;
        private long flushIntervalMs = 200;
        private int retentionDays = 30;
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

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
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

        public FunctionAuditMode resolvedMode() {
            return FunctionAuditMode.parse(mode);
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
    }

    public Java getJava() {
        return java;
    }

    public void setJava(Java java) {
        this.java = java != null ? java : new Java();
    }

    public boolean isJavaEnabled() {
        return java != null && java.isEnabled();
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit != null ? audit : new Audit();
    }
}
