package com.ispf.server.event;

import com.ispf.server.config.EventJournalProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceLimitTest {

    @Mock
    private RecentEventCache recentEventCache;

    @Mock
    private EventJournalProperties eventJournalProperties;

    @Test
    void nonPositiveLimitIsRejected() {
        EventService service = service();

        assertThatThrownBy(() -> service.list(null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event limit must be positive");
        assertThatThrownBy(() -> service.list("root.platform.devices.room", -1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event limit must be positive");
        verifyNoInteractions(recentEventCache);
    }

    @Test
    void positiveLimitStillLists() {
        when(recentEventCache.isEnabled()).thenReturn(false);
        when(eventJournalProperties.isEnabled()).thenReturn(false);

        assertThat(service().list(null, 1)).isEmpty();
    }

    private EventService service() {
        return new EventService(
                null,
                null,
                null,
                null,
                null,
                null,
                recentEventCache,
                eventJournalProperties,
                null,
                null,
                null,
                null
        );
    }
}
