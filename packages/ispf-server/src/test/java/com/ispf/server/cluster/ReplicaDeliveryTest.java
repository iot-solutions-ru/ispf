package com.ispf.server.cluster;

import io.nats.client.Message;
import io.nats.client.impl.NatsJetStreamMetaData;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplicaDeliveryTest {

    @Test
    void coreNatsMessageCannotBeRedelivered() {
        Message message = mock(Message.class);
        when(message.isJetStream()).thenReturn(false);

        ReplicaDelivery delivery = ReplicaDelivery.of(message);
        delivery.ack();
        delivery.nak();

        assertThat(delivery.redeliverable()).isFalse();
        verify(message, never()).ack();
        verify(message, never()).nakWithDelay(any(Duration.class));
    }

    @Test
    void jetStreamAckSettlesTheMessage() {
        Message message = mock(Message.class);
        when(message.isJetStream()).thenReturn(true);

        ReplicaDelivery.of(message).ack();

        verify(message).ack();
    }

    @Test
    void jetStreamNakAsksForRedeliveryWithBackoff() {
        Message message = mock(Message.class);
        NatsJetStreamMetaData metaData = mock(NatsJetStreamMetaData.class);
        when(message.isJetStream()).thenReturn(true);
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(3L);

        ReplicaDelivery delivery = ReplicaDelivery.of(message);
        delivery.nak();

        assertThat(delivery.redeliverable()).isTrue();
        verify(message).nakWithDelay(Duration.ofSeconds(4));
        verify(message, never()).ack();
    }

    @Test
    void redeliveryDelayDoublesUpToTheCap() {
        assertThat(ReplicaDelivery.redeliveryDelay(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(ReplicaDelivery.redeliveryDelay(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(ReplicaDelivery.redeliveryDelay(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(ReplicaDelivery.redeliveryDelay(5)).isEqualTo(Duration.ofSeconds(16));
        assertThat(ReplicaDelivery.redeliveryDelay(6)).isEqualTo(ReplicaDelivery.MAX_REDELIVERY_DELAY);
        assertThat(ReplicaDelivery.redeliveryDelay(1_000)).isEqualTo(ReplicaDelivery.MAX_REDELIVERY_DELAY);
    }
}
