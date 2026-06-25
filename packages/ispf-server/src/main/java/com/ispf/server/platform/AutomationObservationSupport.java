package com.ispf.server.platform;

import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectChangeType;
import com.ispf.server.object.bus.ObjectChangeLane;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Micrometer observations for automation pipeline handlers (exported as OTel traces when enabled).
 */
@Component
public class AutomationObservationSupport {

    static final String HANDLER_OBSERVATION = "ispf.object-change.handler";

    private final Optional<ObservationRegistry> observationRegistry;

    public AutomationObservationSupport(Optional<ObservationRegistry> observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public void observeObjectChangeHandler(
            String handlerName,
            ObjectChangeLane lane,
            ObjectChangeEvent event,
            Runnable action
    ) {
        if (observationRegistry.isEmpty()) {
            action.run();
            return;
        }
        Observation observation = Observation.createNotStarted(HANDLER_OBSERVATION, observationRegistry.get())
                .lowCardinalityKeyValue("handler", handlerName)
                .lowCardinalityKeyValue("lane", lane.name().toLowerCase())
                .lowCardinalityKeyValue("change_type", event.type().name())
                .highCardinalityKeyValue("path", event.path());
        if (event.variableName() != null) {
            observation.highCardinalityKeyValue("variable", event.variableName());
        }
        if (event.type() == ObjectChangeType.EVENT_FIRED && event.variableName() != null) {
            observation.highCardinalityKeyValue("event_name", event.variableName());
        }
        observation.contextualName(handlerName);
        observation.observe(action);
    }
}
