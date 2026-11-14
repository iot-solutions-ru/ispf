package com.ispf.server.application.data;

import com.ispf.server.audit.AuditEventService;
import com.ispf.server.cluster.NatsJetStreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Drains {@code application_outbox} rows whose connector is listed in
 * {@code ispf.application.outbox.nats-connectors}.
 */
@Component
@ConditionalOnBean(NatsJetStreamSupport.class)
public class NatsOutboxPump {

    private static final Logger log = LoggerFactory.getLogger(NatsOutboxPump.class);

    private final ApplicationMessageRelay relay;
    private final NatsOutboxDelivery delivery;
    private final AuditEventService auditEventService;

    @Autowired
    public NatsOutboxPump(
            ApplicationMessageRelay relay,
            NatsJetStreamSupport jetStreamSupport,
            @Value("${ispf.application.outbox.nats-connectors:}") String connectors,
            AuditEventService auditEventService
    ) {
        this.relay = relay;
        this.auditEventService = auditEventService;
        Set<String> parsed = NatsOutboxDelivery.parseConnectors(connectors);
        this.delivery = parsed.isEmpty() || !jetStreamSupport.isActive()
                ? null
                : new NatsOutboxDelivery(jetStreamSupport, parsed);
    }

    @Scheduled(fixedDelayString = "${ispf.application.outbox.delay-ms:5000}")
    public int relayNats() {
        if (delivery == null) {
            return 0;
        }
        try {
            return relay.relayOnce(delivery.connectors(), (appId, connectorId, messageId, body) -> {
                delivery.deliver(appId, connectorId, messageId, body);
                audit(appId, connectorId, messageId);
            });
        } catch (RuntimeException ex) {
            log.warn("outbox NATS relay skipped: {}", ex.getMessage());
            return 0;
        }
    }

    private void audit(String appId, String connectorId, String messageId) {
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
                    Map.of("appId", appId, "connectorId", connectorId, "transport", "nats")
            );
        } catch (RuntimeException auditFailure) {
            log.warn("outbox NATS delivery audit skipped: {}", auditFailure.getMessage());
        }
    }
}
