package com.ispf.server.event;

import com.ispf.server.persistence.EventHistoryRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventHistoryRecordCounterBootstrap {

    private final EventHistoryRepository eventHistoryRepository;
    private final EventHistoryRecordCounter recordCounter;

    public EventHistoryRecordCounterBootstrap(
            EventHistoryRepository eventHistoryRepository,
            EventHistoryRecordCounter recordCounter
    ) {
        this.eventHistoryRepository = eventHistoryRepository;
        this.recordCounter = recordCounter;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(50)
    @Transactional(readOnly = true)
    public void initializeCounter() {
        if (!recordCounter.isInitialized()) {
            recordCounter.initialize(eventHistoryRepository.count());
        }
    }
}
