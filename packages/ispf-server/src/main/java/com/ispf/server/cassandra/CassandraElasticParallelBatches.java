package com.ispf.server.cassandra;

import com.ispf.driver.ingress.ElasticWorkerScaler;
import com.ispf.server.config.CassandraStoreProperties;

/**
 * Resolves concurrent same-partition CQL batch parallelism from backlog (writer queue + pending batches).
 * {@code maxParallelPartitionBatches} is the resource ceiling; elastic mode ramps min→max under load.
 */
public final class CassandraElasticParallelBatches {

    private final ElasticWorkerScaler scaler;

    public CassandraElasticParallelBatches(CassandraStoreProperties settings) {
        this.scaler = new ElasticWorkerScaler(
                settings.resolvedMinParallelPartitionBatches(),
                settings.getMaxParallelPartitionBatches(),
                settings.getElasticParallelScaleUpThreshold(),
                settings.getElasticParallelScaleDownSteps()
        );
    }

    public int resolve(CassandraStoreProperties settings, int queueDepth, int pendingBatches) {
        if (pendingBatches <= 0) {
            return 1;
        }
        if (!settings.isElasticParallelBatchesEnabled()) {
            return Math.min(settings.getMaxParallelPartitionBatches(), pendingBatches);
        }
        syncSettings(settings);
        scaler.adjust(Math.max(queueDepth, pendingBatches));
        return Math.min(scaler.targetWorkers(), pendingBatches);
    }

    public int targetParallelism() {
        return scaler.targetWorkers();
    }

    private void syncSettings(CassandraStoreProperties settings) {
        scaler.reconfigure(
                settings.resolvedMinParallelPartitionBatches(),
                settings.getMaxParallelPartitionBatches(),
                settings.getElasticParallelScaleUpThreshold(),
                settings.getElasticParallelScaleDownSteps()
        );
    }
}
