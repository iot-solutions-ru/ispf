package com.ispf.server.application.data;

import com.ispf.server.audit.AuditEventService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Drains {@code application_outbox} rows whose connector is listed in
 * {@code ispf.application.outbox.http-endpoints} ({@code connector=url} pairs).
 * An empty property leaves every row pending.
 */
@Component
public class ApplicationOutboxPump {

    private static final Logger log = LoggerFactory.getLogger(ApplicationOutboxPump.class);

    private final ApplicationMessageRelay relay;
    private final Map<String, URI> endpoints;
    private final HttpClient client;
    private final HttpOutboxDelivery delivery;
    private final AuditEventService auditEventService;

    public ApplicationOutboxPump(
            ApplicationMessageRelay relay,
            @Value("${ispf.application.outbox.http-endpoints:}") String endpoints
    ) {
        this(relay, endpoints, null);
    }

    @Autowired
    public ApplicationOutboxPump(
            ApplicationMessageRelay relay,
            @Value("${ispf.application.outbox.http-endpoints:}") String endpoints,
            AuditEventService auditEventService
    ) {
        this.relay = relay;
        this.endpoints = HttpOutboxDelivery.parseEndpoints(endpoints);
        this.auditEventService = auditEventService;
        if (this.endpoints.isEmpty()) {
            this.client = null;
            this.delivery = null;
        } else {
            this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            this.delivery = new HttpOutboxDelivery(this.client, this.endpoints);
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    @Scheduled(fixedDelayString = "${ispf.application.outbox.delay-ms:5000}")
    public int relayHttp() {
        if (delivery == null) {
            return 0;
        }
        try {
            return relay.relayOnce(endpoints.keySet(), (appId, connectorId, messageId, body) -> {
                delivery.deliver(appId, connectorId, messageId, body);
                if (auditEventService == null) {
                    return;
                }
                try {
                    auditEventService.logInNewTransaction(
                            "integration",
                            "outbox.deliver",
                            "system",
                            "outbox",
                            messageId,
                            Map.of("appId", appId, "connectorId", connectorId)
                    );
                } catch (RuntimeException auditFailure) {
                    log.warn("outbox delivery audit skipped: {}", auditFailure.getMessage());
                }
            });
        } catch (RuntimeException ex) {
            log.warn("outbox HTTP relay skipped: {}", ex.getMessage());
            return 0;
        }
    }
}
