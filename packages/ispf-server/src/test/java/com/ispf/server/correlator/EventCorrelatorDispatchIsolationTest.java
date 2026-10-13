package com.ispf.server.correlator;

import com.ispf.core.object.EventLevel;
import com.ispf.core.object.ObjectEvent;
import com.ispf.server.automation.AutomationRuleIndex;
import com.ispf.server.event.RecentEventCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EventCorrelatorDispatchIsolationTest {

    private static final String OBJECT_PATH = "root.platform.devices.correlator-dispatch-probe";

    @Autowired
    private EventCorrelatorService correlatorService;

    @Autowired
    private AutomationRuleIndex ruleIndex;

    @Autowired
    private RecentEventCache recentEventCache;

    @Test
    @Transactional
    void usesEventTimeForLastTriggeredAt() {
        EventCorrelator created = index(correlator("timed", "probe-time", "true"));
        rememberEvent("probe-time");
        Instant eventTime = Instant.parse("2099-06-01T12:00:00Z");

        correlatorService.processEventFired(OBJECT_PATH, "probe-time", eventTime);

        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isEqualTo(eventTime);
    }

    @Test
    @Transactional
    void siblingCommitsWhenPeerFilterFailsInOwnTransaction() {
        EventCorrelator broken = index(correlator("tx-bad", "probe-tx", "noSuchFunc(1)"));
        EventCorrelator healthy = index(correlator("tx-good", "probe-tx", "true"));
        rememberEvent("probe-tx");

        correlatorService.processEventFired(OBJECT_PATH, "probe-tx");

        assertThat(correlatorService.get(broken.id()).lastTriggeredAt()).isNull();
        assertThat(correlatorService.get(healthy.id()).lastTriggeredAt()).isNotNull();
    }

    private EventCorrelator index(EventCorrelatorService.CreateCorrelatorRequest request) {
        EventCorrelator created = correlatorService.create(request);
        ruleIndex.addCorrelator(created);
        return created;
    }

    private static EventCorrelatorService.CreateCorrelatorRequest correlator(
            String name,
            String eventName,
            String filter
    ) {
        return new EventCorrelatorService.CreateCorrelatorRequest(
                name,
                OBJECT_PATH,
                CorrelatorPatternType.COUNT,
                eventName,
                null,
                60,
                1,
                0,
                0,
                CorrelatorActionType.SET_VARIABLE,
                "flag=1",
                filter,
                true
        );
    }

    private void rememberEvent(String eventName) {
        recentEventCache.append(ObjectEvent.of(OBJECT_PATH, eventName, EventLevel.INFO, null, Instant.now()));
    }
}
