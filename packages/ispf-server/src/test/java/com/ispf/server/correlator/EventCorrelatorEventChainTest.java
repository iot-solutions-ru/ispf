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
class EventCorrelatorEventChainTest {

    @Autowired
    private EventCorrelatorService correlatorService;

    @Autowired
    private AutomationRuleIndex ruleIndex;

    @Test
    @Transactional
    void eventChainTriggersOnlyInOrderWithinWindow() {
        EventCorrelator created = correlatorService.create(new EventCorrelatorService.CreateCorrelatorRequest(
                "Test event chain",
                "root.platform.singleton-blueprints.mes-platform-hub-v1",
                CorrelatorPatternType.EVENT_CHAIN,
                "stepA",
                "stepB,stepC",
                120,
                1,
                0,
                0,
                CorrelatorActionType.SET_VARIABLE,
                "chainLatched=true",
                true
        ));
        ruleIndex.addCorrelator(created);

        correlatorService.processEventFired("root.platform.singleton-blueprints.mes-platform-hub-v1", "stepA");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.singleton-blueprints.mes-platform-hub-v1", "stepC");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.singleton-blueprints.mes-platform-hub-v1", "stepB");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.singleton-blueprints.mes-platform-hub-v1", "stepC");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNotNull();
    }
}
