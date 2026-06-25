package com.ispf.server.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class EventHistoryRecordCounterBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EventHistoryRecordCounterBootstrap.class);

    private final EventJournalStore eventJournalStore;
    private final EventHistoryRecordCounter recordCounter;

    public EventHistoryRecordCounterBootstrap(
            EventJournalStore eventJournalStore,
            EventHistoryRecordCounter recordCounter
    ) {
        this.eventJournalStore = eventJournalStore;
        this.recordCounter = recordCounter;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(50)
    public void initializeCounter() {
        if (recordCounter.isInitialized()) {
            return;
        }
        try {
            recordCounter.initialize(eventJournalStore.countTotal());
        } catch (Exception ex) {
            log.warn("Event history counter bootstrap failed, starting from 0: {}", ex.getMessage());
            recordCounter.initialize(0);
        }
    }
}
