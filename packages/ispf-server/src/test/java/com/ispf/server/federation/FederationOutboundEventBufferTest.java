package com.ispf.server.federation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FederationOutboundEventBufferTest {

    @Test
    void enqueuesAndDrainsInOrder() {
        FederationOutboundEventBuffer buffer = new FederationOutboundEventBuffer(4096, FederationOutboundEventBuffer.DropPolicy.DROP_OLDEST);
        buffer.enqueue("root.platform.devices.a", "temperature", null);
        buffer.enqueue("root.platform.devices.b", "pressure", null);
        assertThat(buffer.stats().count()).isEqualTo(2);
        var drained = buffer.drainOrdered();
        assertThat(drained).hasSize(2);
        assertThat(drained.get(0).path()).isEqualTo("root.platform.devices.a");
        assertThat(drained.get(1).path()).isEqualTo("root.platform.devices.b");
        assertThat(buffer.isEmpty()).isTrue();
    }

    @Test
    void dropOldestWhenFull() {
        FederationOutboundEventBuffer buffer = new FederationOutboundEventBuffer(200, FederationOutboundEventBuffer.DropPolicy.DROP_OLDEST);
        buffer.enqueue("root.platform.devices.one", "v1", null);
        buffer.enqueue("root.platform.devices.two", "v2", null);
        buffer.enqueue("root.platform.devices.three", "v3", null);
        assertThat(buffer.stats().dropped()).isGreaterThan(0);
        assertThat(buffer.drainOrdered()).isNotEmpty();
    }
}
