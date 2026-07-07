package com.ispf.server.correlator;

import com.ispf.server.automation.AutomationRuleIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EventCorrelatorWindowTest {

    @Autowired
    private EventCorrelatorService correlatorService;

    @Autowired
    private AutomationRuleIndex ruleIndex;

    private EventCorrelator createAndIndex(EventCorrelatorService.CreateCorrelatorRequest request) {
        EventCorrelator created = correlatorService.create(request);
        ruleIndex.addCorrelator(created);
        return created;
    }

    @Test
    @Transactional
    void windowPatternTriggersWhenAllEventsSeenWithinWindow() {
        EventCorrelator created = createAndIndex(new EventCorrelatorService.CreateCorrelatorRequest(
                "Test window",
                "root.platform.devices.mes-platform-hub",
                CorrelatorPatternType.WINDOW,
                "workOrderCreated",
                "workOrderReleased,workOrderStarted",
                120,
                1,
                0,
                0,
                CorrelatorActionType.RUN_WORKFLOW,
                "root.platform.workflows.mes-work-order-dispatch",
                true
        ));

        correlatorService.processEventFired("root.platform.devices.mes-platform-hub", "workOrderCreated");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.devices.mes-platform-hub", "workOrderReleased");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.devices.mes-platform-hub", "workOrderStarted");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNotNull();
    }
}
