package com.ispf.server.object.pubsub;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectWebSocketPathInterestRegistryTest {

    @Test
    void emptyRegistryHasNoInterest() {
        ObjectWebSocketPathInterestRegistry registry = new ObjectWebSocketPathInterestRegistry();
        assertThat(registry.hasPathInterest("root.dev.sensor")).isFalse();
        assertThat(registry.hasVariableInterest("root.dev.sensor", "temperature")).isFalse();
    }

    @Test
    void pathWideCoversAllVariables() {
        ObjectWebSocketPathInterestRegistry registry = new ObjectWebSocketPathInterestRegistry();
        registry.subscribePath("root.dev.sensor");

        assertThat(registry.hasVariableInterest("root.dev.sensor", "temperature")).isTrue();
        assertThat(registry.hasVariableInterest("root.dev.sensor", "humidity")).isTrue();
        assertThat(registry.hasPathInterest("root.dev.sensor")).isTrue();
    }

    @Test
    void variableInterestIsNarrow() {
        ObjectWebSocketPathInterestRegistry registry = new ObjectWebSocketPathInterestRegistry();
        registry.subscribeVariables("root.dev.sensor", List.of("temperature"));

        assertThat(registry.hasVariableInterest("root.dev.sensor", "temperature")).isTrue();
        assertThat(registry.hasVariableInterest("root.dev.sensor", "humidity")).isFalse();
        assertThat(registry.hasPathInterest("root.dev.sensor")).isTrue();
    }

    @Test
    void unsubscribeClearsInterest() {
        ObjectWebSocketPathInterestRegistry registry = new ObjectWebSocketPathInterestRegistry();
        registry.subscribeVariables("root.dev.sensor", Set.of("temperature"));
        registry.unsubscribeVariables("root.dev.sensor", Set.of("temperature"));

        assertThat(registry.hasVariableInterest("root.dev.sensor", "temperature")).isFalse();
        assertThat(registry.hasPathInterest("root.dev.sensor")).isFalse();
    }

    @Test
    void broadcastHooksAreNoOps() {
        ObjectWebSocketPathInterestRegistry registry = new ObjectWebSocketPathInterestRegistry();
        registry.onBroadcastSessionAdded();
        registry.onBroadcastSessionAdded();
        assertThat(registry.hasPathInterest("root.dev.sensor")).isFalse();
        registry.onBroadcastSessionRemoved();
        assertThat(registry.hasPathInterest("root.dev.sensor")).isFalse();
    }
}
