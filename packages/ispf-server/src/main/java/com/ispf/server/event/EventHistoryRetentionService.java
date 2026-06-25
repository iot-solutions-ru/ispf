package com.ispf.server.event;

import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.history.TimescaleHypertableInitializer;
import com.ispf.server.persistence.EventHistoryRepository;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class EventHistoryRetentionService {

    static final String RETENTION_LOCK = "event-history-retention";

    private final EventHistoryRepository eventHistoryRepository;
    private final EventJournalProperties properties;
    private final TimescaleHypertableInitializer timescaleHypertableInitializer;
    private final PlatformLeaderLockService leaderLockService;

    public EventHistoryRetentionService(
            EventHistoryRepository eventHistoryRepository,
            EventJournalProperties properties,
            TimescaleHypertableInitializer timescaleHypertableInitializer,
            PlatformLeaderLockService leaderLockService
    ) {
        this.eventHistoryRepository = eventHistoryRepository;
        this.properties = properties;
        this.timescaleHypertableInitializer = timescaleHypertableInitializer;
        this.leaderLockService = leaderLockService;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredEvents() {
        if (!leaderLockService.tryAcquire(RETENTION_LOCK, Duration.ofHours(1))) {
            return;
        }
        try {
            purgeExpiredEventsInternal();
        } finally {
            leaderLockService.release(RETENTION_LOCK);
        }
    }

    void purgeExpiredEventsInternal() {
        if (timescaleHypertableInitializer.isEventHistoryTimescaleActive()) {
            return;
        }
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        eventHistoryRepository.deleteByOccurredAtBefore(cutoff);
    }
}
