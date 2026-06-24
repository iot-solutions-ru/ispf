package com.ispf.server.event;

import com.ispf.server.persistence.EventHistoryRepository;
import com.ispf.server.persistence.entity.EventHistoryEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EventJournalBatchPersister {

    private final EventHistoryRepository eventHistoryRepository;

    public EventJournalBatchPersister(EventHistoryRepository eventHistoryRepository) {
        this.eventHistoryRepository = eventHistoryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBatch(List<EventHistoryEntity> batch) {
        if (batch.isEmpty()) {
            return;
        }
        eventHistoryRepository.saveAll(batch);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistOne(EventHistoryEntity entity) {
        eventHistoryRepository.save(entity);
    }
}
