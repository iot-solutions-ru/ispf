package com.ispf.server.application.data;

import com.ispf.server.audit.AuditEventService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Drains {@code application_outbox} rows whose connector is listed in
 * {@code ispf.application.outbox.kafka-topics} ({@code connector=topic} pairs).
 * Requires {@code ispf.application.outbox.kafka-bootstrap}.
 */
@Component
public class KafkaOutboxPump {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPump.class);

    private final ApplicationMessageRelay relay;
    private final KafkaOutboxDelivery delivery;
    private final AuditEventService auditEventService;

    @Autowired
    public KafkaOutboxPump(
            ApplicationMessageRelay relay,
            @Value("${ispf.application.outbox.kafka-bootstrap:}") String bootstrap,
            @Value("${ispf.application.outbox.kafka-topics:}") String topics,
            AuditEventService auditEventService
    ) {
        this.relay = relay;
        this.auditEventService = auditEventService;
        Map<String, String> parsed = KafkaOutboxDelivery.parseTopics(topics);
        if (bootstrap == null || bootstrap.isBlank() || parsed.isEmpty()) {
            this.delivery = null;
        } else {
            this.delivery = new KafkaOutboxDelivery(bootstrap, parsed);
        }
    }

    KafkaOutboxPump(ApplicationMessageRelay relay, KafkaOutboxDelivery delivery, AuditEventService audit) {
        this.relay = relay;
        this.delivery = delivery;
        this.auditEventService = audit;
    }

    @PreDestroy
    public void close() {
        if (delivery != null) {
            delivery.close();
        }
    }

    @Scheduled(fixedDelayString = "${ispf.application.outbox.delay-ms:5000}")
    public int relayKafka() {
        if (delivery == null) {
            return 0;
        }
        try {
            return relay.relayOnce(delivery.topics().keySet(), (appId, connectorId, messageId, body) -> {
                delivery.deliver(appId, connectorId, messageId, body);
                audit(appId, connectorId, messageId);
            });
        } catch (RuntimeException ex) {
            log.warn("outbox Kafka relay skipped: {}", ex.getMessage());
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
                    Map.of("appId", appId, "connectorId", connectorId, "transport", "kafka")
            );
        } catch (RuntimeException auditFailure) {
            log.warn("outbox Kafka delivery audit skipped: {}", auditFailure.getMessage());
        }
    }
}
