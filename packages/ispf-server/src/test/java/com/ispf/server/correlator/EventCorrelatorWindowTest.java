package com.ispf.server.correlator;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EventCorrelatorWindowTest {

    @Autowired
    private EventCorrelatorService correlatorService;

    @Autowired
    private AutomationRuleIndex ruleIndex;

    @Autowired
    private RecentEventCache recentEventCache;

    private static final DataSchema ALERT_PAYLOAD = DataSchema.builder("alertFire")
            .field("targetObjectPath", FieldType.STRING)
            .build();

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
                "root.platform.singleton-blueprints.mes-platform-hub-v1",
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

        correlatorService.processEventFired("root.platform.singleton-blueprints.mes-platform-hub-v1", "workOrderCreated");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.singleton-blueprints.mes-platform-hub-v1", "workOrderReleased");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.singleton-blueprints.mes-platform-hub-v1", "workOrderStarted");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNotNull();
    }

    @Test
    @Transactional
    void windowHitsFromTwoAlertsShareTheWatchedDevice() {
        String device = "root.platform.devices.window-hit-probe";
        EventCorrelator created = createAndIndex(new EventCorrelatorService.CreateCorrelatorRequest(
                "window-alerts",
                device,
                CorrelatorPatternType.WINDOW,
                "raiseA",
                "raiseB",
                120,
                1,
                0,
                0,
                CorrelatorActionType.SET_VARIABLE,
                "flag=1",
                true
        ));
        rememberAlert("root.platform.alert-rules.win-a", "raiseA", device);
        rememberAlert("root.platform.alert-rules.win-b", "raiseB", device);

        correlatorService.processEventFired("root.platform.alert-rules.win-a", "raiseA");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNull();

        correlatorService.processEventFired("root.platform.alert-rules.win-b", "raiseB");
        assertThat(correlatorService.get(created.id()).lastTriggeredAt()).isNotNull();
    }

    private void rememberAlert(String alertPath, String eventName, String devicePath) {
        recentEventCache.append(ObjectEvent.of(
                alertPath,
                eventName,
                EventLevel.INFO,
                DataRecord.single(ALERT_PAYLOAD, Map.of("targetObjectPath", devicePath)),
                Instant.now()
        ));
    }
}
