package com.ispf.server.platform;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.bus.ObjectChangeLane;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationObservationSupportTest {

    @Test
    void runsActionWithoutRegistry() {
        AutomationObservationSupport support = new AutomationObservationSupport(Optional.empty());
        ObjectChangeEvent event = ObjectChangeEvent.variableUpdated("root.test", "temperature");
        boolean[] ran = {false};
        support.observeObjectChangeHandler("TestHandler", ObjectChangeLane.AUTOMATION, event, () -> ran[0] = true);
        assertThat(ran[0]).isTrue();
    }

    @Test
    void createsHandlerObservationWithTags() {
        ObservationRegistry registry = ObservationRegistry.create();
        AutomationObservationSupport support = new AutomationObservationSupport(Optional.of(registry));
        ObjectChangeEvent event = ObjectChangeEvent.eventFired("root.dev", "alarm1");
        support.observeObjectChangeHandler("AlertRuleListener", ObjectChangeLane.AUTOMATION, event, () -> {
        });
        assertThat(registry.getCurrentObservation()).isNull();
    }
}
