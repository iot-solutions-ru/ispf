package com.ispf.server.cassandra;

import com.ispf.server.config.CassandraStoreProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CassandraElasticParallelBatchesTest {

    @Test
    void rampsParallelismWithQueueBacklogUpToCeiling() {
        CassandraStoreProperties settings = new CassandraStoreProperties();
        settings.setMinParallelPartitionBatches(1);
        settings.setMaxParallelPartitionBatches(16);
        settings.setElasticParallelScaleUpThreshold(10);
        settings.setElasticParallelScaleDownSteps(3);
        CassandraElasticParallelBatches elastic = new CassandraElasticParallelBatches(settings);

        assertEquals(1, elastic.resolve(settings, 0, 8));

        assertEquals(8, elastic.resolve(settings, 500, 8));
        assertEquals(16, elastic.resolve(settings, 500, 32));

        assertEquals(12, elastic.resolve(settings, 0, 12));
    }

    @Test
    void staticModeUsesConfiguredCeiling() {
        CassandraStoreProperties settings = new CassandraStoreProperties();
        settings.setElasticParallelBatchesEnabled(false);
        settings.setMaxParallelPartitionBatches(8);
        CassandraElasticParallelBatches elastic = new CassandraElasticParallelBatches(settings);

        assertEquals(8, elastic.resolve(settings, 10_000, 32));
        assertEquals(3, elastic.resolve(settings, 0, 3));
    }
}
