package com.ispf.server.application.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

/**
 * Drains {@code application_outbox} to the HTTP endpoints in
 * {@code ispf.application.outbox.http-endpoints} ({@code connector=url} pairs).
 * An empty property leaves the rows pending for a caller-supplied delivery.
 */
@Component
public class ApplicationOutboxPump {

    private static final Logger log = LoggerFactory.getLogger(ApplicationOutboxPump.class);

    private final ApplicationMessageRelay relay;
    private final Map<String, URI> endpoints;

    public ApplicationOutboxPump(
            ApplicationMessageRelay relay,
            @Value("${ispf.application.outbox.http-endpoints:}") String endpoints
    ) {
        this.relay = relay;
        this.endpoints = HttpOutboxDelivery.parseEndpoints(endpoints);
    }

    @Scheduled(fixedDelayString = "${ispf.application.outbox.delay-ms:5000}")
    public int relayHttp() {
        if (endpoints.isEmpty()) {
            return 0;
        }
        try {
            return relay.relayOnce(new HttpOutboxDelivery(endpoints));
        } catch (RuntimeException ex) {
            log.warn("outbox HTTP relay skipped: {}", ex.getMessage());
            return 0;
        }
    }
}
