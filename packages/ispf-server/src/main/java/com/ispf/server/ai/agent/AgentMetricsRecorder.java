package com.ispf.server.ai.agent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AgentMetricsRecorder {

    private final Optional<MeterRegistry> meterRegistry;

    public AgentMetricsRecorder(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.ifPresent(this::registerMeters);
    }

    private void registerMeters(MeterRegistry registry) {
        Counter.builder("ispf.agent.turns.started.total").register(registry);
        Counter.builder("ispf.agent.turns.rate_limited.total").register(registry);
        Counter.builder("ispf.agent.guard.blocks.total").register(registry);
    }

    public void recordTurnStarted() {
        increment("ispf.agent.turns.started.total");
    }

    public void recordRateLimited() {
        increment("ispf.agent.turns.rate_limited.total");
    }

    public void recordGuardBlock(String guard) {
        meterRegistry.ifPresent(registry ->
                Counter.builder("ispf.agent.guard.blocks.total")
                        .tag("guard", guard != null ? guard : "unknown")
                        .register(registry)
                        .increment()
        );
    }

    private void increment(String name) {
        meterRegistry.ifPresent(registry -> Counter.builder(name).register(registry).increment());
    }
}
