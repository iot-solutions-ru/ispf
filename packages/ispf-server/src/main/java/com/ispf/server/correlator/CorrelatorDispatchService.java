package com.ispf.server.correlator;

import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.config.CorrelatorProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Routes {@code EVENT_FIRED} to correlators: either inline (tests / sync mode) or keyed async lanes.
 */
@Service
public class CorrelatorDispatchService {

    private final EventCorrelatorService correlatorService;
    private final AutomationTreeService automationTreeService;
    private final CorrelatorProperties properties;
    private final CorrelatorKeyedExecutor keyedExecutor;

    public CorrelatorDispatchService(
            EventCorrelatorService correlatorService,
            AutomationTreeService automationTreeService,
            CorrelatorProperties properties,
            CorrelatorKeyedExecutor keyedExecutor
    ) {
        this.correlatorService = correlatorService;
        this.automationTreeService = automationTreeService;
        this.properties = properties;
        this.keyedExecutor = keyedExecutor;
    }

    public void dispatchEventFired(String objectPath, String eventName, Instant occurredAt) {
        Instant eventTime = occurredAt != null ? occurredAt : Instant.now();
        if (!properties.isAsyncDispatch()) {
            correlatorService.processEventFired(objectPath, eventName, eventTime);
            return;
        }
        List<EventCorrelator> enabled = automationTreeService.findEnabledCorrelatorsForEvent(eventName);
        LinkedHashMap<String, EventCorrelator> correlators = new LinkedHashMap<>();
        for (EventCorrelator correlator : enabled) {
            correlators.put(correlator.id(), correlator);
        }
        for (EventCorrelator correlator : correlators.values()) {
            String correlatorId = correlator.id();
            keyedExecutor.execute(
                    correlatorId,
                    () -> correlatorService.processOneCorrelator(correlatorId, objectPath, eventName, eventTime)
            );
        }
        correlatorService.purgeWindows(eventTime);
    }
}
