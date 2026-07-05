package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CassandraStorePropertiesTest {

    @Test
    void resolveTableUsesDefaultWhenBlank() {
        CassandraStoreProperties properties = new CassandraStoreProperties();
        properties.setTable("  ");
        assertEquals("event_history", properties.resolveTable("event_history"));
    }

    @Test
    void resolveTableUsesConfiguredValue() {
        CassandraStoreProperties properties = new CassandraStoreProperties();
        properties.setTable("custom_samples");
        assertEquals("custom_samples", properties.resolveTable("variable_samples"));
    }

    @Test
    void eventJournalRecognizesCassandraAndScyllaStores() {
        EventJournalProperties properties = new EventJournalProperties();
        properties.setStore("cassandra");
        assertTrue(properties.isCassandraStore());
        assertTrue(properties.isExternalTimeSeriesStore());
        assertFalse(properties.isJdbcStore());

        properties.setStore("scylla");
        assertTrue(properties.isCassandraStore());

        properties.setStore("jdbc");
        assertFalse(properties.isCassandraStore());
        assertTrue(properties.isJdbcStore());
    }

    @Test
    void variableHistoryRecognizesCassandraStore() {
        VariableHistoryProperties properties = new VariableHistoryProperties();
        properties.setStore("cassandra");
        assertTrue(properties.isCassandraStore());
        assertTrue(properties.isExternalTimeSeriesStore());

        properties.setStore("clickhouse");
        assertFalse(properties.isCassandraStore());
        assertTrue(properties.isExternalTimeSeriesStore());
    }

    @Test
    void cassandraWriteTuningDefaultsAndClamps() {
        CassandraStoreProperties properties = new CassandraStoreProperties();
        assertEquals(200, properties.getMaxStatementsPerPartitionBatch());
        assertEquals(32, properties.getMaxParallelPartitionBatches());
        assertEquals(1, properties.getMinParallelPartitionBatches());
        assertTrue(properties.isElasticParallelBatchesEnabled());

        properties.setMaxStatementsPerPartitionBatch(0);
        properties.setMaxParallelPartitionBatches(-3);
        properties.setMinParallelPartitionBatches(64);
        assertEquals(1, properties.getMaxStatementsPerPartitionBatch());
        assertEquals(1, properties.getMaxParallelPartitionBatches());
        assertEquals(1, properties.resolvedMinParallelPartitionBatches());
    }

    @Test
    void eventJournalCassandraWriteFlagsDefaults() {
        EventJournalProperties properties = new EventJournalProperties();
        assertFalse(properties.isEnabled());
        assertFalse(properties.isCassandraGlobalTableEnabled());
        assertTrue(properties.isCassandraAsyncCounterUpdate());
    }

    @Test
    void eventJournalQueueTuningClamps() {
        EventJournalProperties properties = new EventJournalProperties();
        properties.setQueueCapacity(0);
        properties.setBatchSize(-1);
        properties.setFlushIntervalMs(0);
        properties.setWriterThreads(0);
        assertEquals(1, properties.getQueueCapacity());
        assertEquals(1, properties.getBatchSize());
        assertEquals(1, properties.getFlushIntervalMs());
        assertEquals(1, properties.getWriterThreads());
    }
}
