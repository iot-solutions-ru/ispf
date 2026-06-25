package com.ispf.server.event;

import com.ispf.server.config.EventJournalProperties;
import com.ispf.server.history.TimescaleHypertableInitializer;
import com.ispf.server.persistence.EventHistoryRepository;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventHistoryRetentionServiceTest {

    @Mock
    private EventHistoryRepository eventHistoryRepository;
    @Mock
    private TimescaleHypertableInitializer timescaleHypertableInitializer;
    @Mock
    private PlatformLeaderLockService leaderLockService;

    private EventJournalProperties properties;
    private EventHistoryRetentionService service;

    @BeforeEach
    void setUp() {
        properties = new EventJournalProperties();
        properties.setRetentionDays(90);
        service = new EventHistoryRetentionService(
                eventHistoryRepository,
                properties,
                timescaleHypertableInitializer,
                leaderLockService
        );
    }

    @Test
    void skipsPurgeWhenTimescaleRetentionActive() {
        when(timescaleHypertableInitializer.isEventHistoryTimescaleActive()).thenReturn(true);

        service.purgeExpiredEventsInternal();

        verify(eventHistoryRepository, never()).deleteByOccurredAtBefore(any());
    }

    @Test
    void purgesRowsOlderThanRetentionWhenTimescaleInactive() {
        when(timescaleHypertableInitializer.isEventHistoryTimescaleActive()).thenReturn(false);

        service.purgeExpiredEventsInternal();

        verify(eventHistoryRepository).deleteByOccurredAtBefore(org.mockito.ArgumentMatchers.argThat(cutoff ->
                cutoff.isBefore(Instant.now().minus(89, ChronoUnit.DAYS))
                        && cutoff.isAfter(Instant.now().minus(91, ChronoUnit.DAYS))
        ));
    }

    @Test
    void skipsPurgeWhenRetentionDisabled() {
        properties.setRetentionDays(0);

        service.purgeExpiredEventsInternal();

        verify(eventHistoryRepository, never()).deleteByOccurredAtBefore(any());
    }
}
