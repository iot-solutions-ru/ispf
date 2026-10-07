package com.ispf.server.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventHistoryRecordCounterBootstrapTest {

    @Test
    void countFailureLeavesTheCounterUninitialized() {
        EventJournalStore store = mock(EventJournalStore.class);
        when(store.countTotal()).thenThrow(new IllegalStateException("db down"));
        EventHistoryRecordCounter counter = new EventHistoryRecordCounter();
        EventHistoryRecordCounterBootstrap bootstrap = new EventHistoryRecordCounterBootstrap(store, counter);

        bootstrap.initializeCounter();

        assertFalse(counter.isInitialized());
        assertEquals(0, counter.totalRecords());
    }

    @Test
    void countSuccessInitializesFromTheStore() {
        EventJournalStore store = mock(EventJournalStore.class);
        when(store.countTotal()).thenReturn(12L);
        EventHistoryRecordCounter counter = new EventHistoryRecordCounter();
        EventHistoryRecordCounterBootstrap bootstrap = new EventHistoryRecordCounterBootstrap(store, counter);

        bootstrap.initializeCounter();

        assertTrue(counter.isInitialized());
        assertEquals(12L, counter.totalRecords());
    }
}
