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
}
