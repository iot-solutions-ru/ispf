package com.ispf.server.cluster;

import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Broker settlement of one replica message. JetStream messages are acked once handled and nak'd with
 * backoff when they cannot be handled, so the broker redelivers them; core NATS has no redelivery.
 */
interface ReplicaDelivery {

    Duration MAX_REDELIVERY_DELAY = Duration.ofSeconds(30);

    ReplicaDelivery NONE = new ReplicaDelivery() {
        @Override
        public void ack() {
        }

        @Override
        public void nak() {
        }

        @Override
        public boolean redeliverable() {
            return false;
        }
    };

    /** Applied, ignored, or superseded by a newer pending event for the same key. */
    void ack();

    /** Not handled; the broker should deliver it again later. */
    void nak();

    /** Whether {@link #nak()} brings the message back. */
    boolean redeliverable();

    static ReplicaDelivery of(Message message) {
        return message.isJetStream() ? new JetStream(message) : NONE;
    }

    static Duration redeliveryDelay(long deliveredCount) {
        long attempt = Math.min(Math.max(deliveredCount, 1), 6);
        Duration delay = Duration.ofSeconds(1L << (attempt - 1));
        return delay.compareTo(MAX_REDELIVERY_DELAY) > 0 ? MAX_REDELIVERY_DELAY : delay;
    }

    record JetStream(Message message) implements ReplicaDelivery {

        private static final Logger log = LoggerFactory.getLogger(ReplicaDelivery.class);

        @Override
        public void ack() {
            try {
                message.ack();
            } catch (RuntimeException ex) {
                log.debug("JetStream replica ack failed (broker redelivers after ack wait): {}", ex.getMessage());
            }
        }

        @Override
        public void nak() {
            try {
                message.nakWithDelay(redeliveryDelay(deliveredCount()));
            } catch (RuntimeException ex) {
                log.debug("JetStream replica nak failed (broker redelivers after ack wait): {}", ex.getMessage());
            }
        }

        @Override
        public boolean redeliverable() {
            return true;
        }

        private long deliveredCount() {
            try {
                return message.metaData().deliveredCount();
            } catch (RuntimeException ex) {
                return 1;
            }
        }
    }
}
