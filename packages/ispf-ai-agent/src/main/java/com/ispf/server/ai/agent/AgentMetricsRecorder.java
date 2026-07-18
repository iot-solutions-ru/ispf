package com.ispf.server.ai.agent;

import com.ispf.server.platform.AgentMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AgentMetricsRecorder implements AgentMetricsPort {

    private static final Duration TURN_WINDOW = Duration.ofHours(1);

    private final Optional<MeterRegistry> meterRegistry;
    private final AtomicLong turnsCompleted = new AtomicLong();
    private final AtomicLong stepsCompletedTotal = new AtomicLong();
    private final ConcurrentLinkedDeque<Instant> recentTurnStarts = new ConcurrentLinkedDeque<>();
    private final Map<String, AtomicLong> guardBlocksByType = new LinkedHashMap<>();

    public AgentMetricsRecorder(Optional<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.ifPresent(this::registerMeters);
        guardBlocksByType.put("planGuard", new AtomicLong());
        guardBlocksByType.put("mutateApproval", new AtomicLong());
        guardBlocksByType.put("groundTruth", new AtomicLong());
        guardBlocksByType.put("widgetBinding", new AtomicLong());
        guardBlocksByType.put("loopGuard", new AtomicLong());
        guardBlocksByType.put("operatorTurn", new AtomicLong());
    }

    private void registerMeters(MeterRegistry registry) {
        Counter.builder("ispf.agent.turns.started.total").register(registry);
        Counter.builder("ispf.agent.turns.rate_limited.total").register(registry);
        Counter.builder("ispf.agent.turns.completed.total").register(registry);
        Counter.builder("ispf.agent.guard.blocks.total").register(registry);
        registry.gauge("ispf.agent.turns.last_hour", this, AgentMetricsRecorder::turnsLastHourGauge);
        registry.gauge("ispf.agent.turn.steps.avg", this, AgentMetricsRecorder::avgStepsPerTurnGauge);
    }

    public void recordTurnStarted() {
        increment("ispf.agent.turns.started.total");
        pruneOldTurnStarts();
        recentTurnStarts.addLast(Instant.now());
    }

    public void recordTurnCompleted(int stepCount) {
        turnsCompleted.incrementAndGet();
        if (stepCount > 0) {
            stepsCompletedTotal.addAndGet(stepCount);
        }
        increment("ispf.agent.turns.completed.total");
    }

    public void recordRateLimited() {
        increment("ispf.agent.turns.rate_limited.total");
    }

    public void recordGuardBlock(String guard) {
        String key = guard != null && !guard.isBlank() ? guard : "unknown";
        guardBlocksByType.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        meterRegistry.ifPresent(registry ->
                Counter.builder("ispf.agent.guard.blocks.total")
                        .tag("guard", key)
                        .register(registry)
                        .increment()
        );
    }

    public Map<String, Object> agentSnapshot() {
        pruneOldTurnStarts();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("turnsStartedTotal", counterValue("ispf.agent.turns.started.total"));
        snapshot.put("turnsCompletedTotal", turnsCompleted.get());
        snapshot.put("turnsRateLimitedTotal", counterValue("ispf.agent.turns.rate_limited.total"));
        snapshot.put("turnsLastHour", recentTurnStarts.size());
        long completed = turnsCompleted.get();
        long steps = stepsCompletedTotal.get();
        snapshot.put("avgStepsPerTurn", completed > 0 ? round1(steps / (double) completed) : 0.0);
        Map<String, Object> guardBlocks = new LinkedHashMap<>();
        guardBlocksByType.forEach((guard, count) -> {
            if (count.get() > 0) {
                guardBlocks.put(guard, count.get());
            }
        });
        snapshot.put("guardBlocksByType", guardBlocks);
        return snapshot;
    }

    private double turnsLastHourGauge() {
        pruneOldTurnStarts();
        return recentTurnStarts.size();
    }

    private double avgStepsPerTurnGauge() {
        long completed = turnsCompleted.get();
        if (completed <= 0) {
            return 0.0;
        }
        return stepsCompletedTotal.get() / (double) completed;
    }

    private void pruneOldTurnStarts() {
        Instant cutoff = Instant.now().minus(TURN_WINDOW);
        while (true) {
            Instant oldest = recentTurnStarts.peekFirst();
            if (oldest == null || !oldest.isBefore(cutoff)) {
                return;
            }
            recentTurnStarts.pollFirst();
        }
    }

    private long counterValue(String name) {
        return meterRegistry
                .map(registry -> {
                    Counter counter = registry.find(name).counter();
                    return counter != null ? (long) counter.count() : 0L;
                })
                .orElse(0L);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private void increment(String name) {
        meterRegistry.ifPresent(registry -> Counter.builder(name).register(registry).increment());
    }
}
