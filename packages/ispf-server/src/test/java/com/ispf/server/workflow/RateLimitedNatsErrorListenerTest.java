package com.ispf.server.workflow;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitedNatsErrorListenerTest {

    @Test
    void rateLimitsSlowConsumerWarnings() {
        RateLimitedNatsErrorListener listener = new RateLimitedNatsErrorListener(60);
        Connection conn = mock(Connection.class);
        Consumer consumer = mock(Consumer.class);
        when(consumer.getDroppedCount()).thenReturn(3L);

        listener.slowConsumerDetected(conn, consumer);
        listener.slowConsumerDetected(conn, consumer);
        listener.slowConsumerDetected(conn, consumer);

        verify(consumer, times(1)).getDroppedCount();
    }
}
