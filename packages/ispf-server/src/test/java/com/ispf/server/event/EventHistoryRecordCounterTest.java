package com.ispf.server.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventHistoryRecordCounterTest {

    @Test
    void tracksPersistedRowsAfterInitialization() {
        EventHistoryRecordCounter counter = new EventHistoryRecordCounter();
        assertFalse(counter.isInitialized());
        assertEquals(0, counter.totalRecords());

        counter.initialize(1000);
        assertTrue(counter.isInitialized());
        assertEquals(1000, counter.totalRecords());

        counter.recordPersisted(40);
        assertEquals(1040, counter.totalRecords());
    }
}
