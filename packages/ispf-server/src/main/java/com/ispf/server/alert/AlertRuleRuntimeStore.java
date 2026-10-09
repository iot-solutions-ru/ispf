package com.ispf.server.alert;

import com.ispf.core.object.PlatformObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class AlertRuleRuntimeStore {

    /** Evaluations write it while the flusher snapshots it; every field access holds its monitor. */
    private static final class MutableState {
        private Boolean lastConditionMet;
        private Double lastWatchValue;
        private Instant lastFiredAt;
        private Instant conditionTrueSince;
        private Boolean latchedActive;
        private Instant deactivateTrueSince;
        private boolean dirty;

        synchronized void update(Consumer<MutableState> change) {
            change.accept(this);
            dirty = true;
        }

        synchronized AlertRuleRuntimeState snapshot() {
            return new AlertRuleRuntimeState(
                    lastConditionMet,
                    lastWatchValue,
                    lastFiredAt,
                    conditionTrueSince,
                    latchedActive,
                    deactivateTrueSince
            );
        }

        synchronized boolean isDirty() {
            return dirty;
        }

        synchronized void markCleanIfUnchanged(AlertRuleRuntimeState persisted) {
            if (snapshot().equals(persisted)) {
                dirty = false;
            }
        }
    }

    private final ConcurrentHashMap<String, MutableState> states = new ConcurrentHashMap<>();

    public AlertRuleRuntimeState snapshot(String path, PlatformObject node) {
        return ensureLoaded(path, node).snapshot();
    }

    public Double getLastWatchValue(String path, PlatformObject node) {
        return snapshot(path, node).lastWatchValue();
    }

    public void setLastConditionMet(String path, boolean lastConditionMet) {
        update(path, state -> state.lastConditionMet = lastConditionMet);
    }

    public void setLastWatchValue(String path, Double value) {
        update(path, state -> state.lastWatchValue = value);
    }

    public void setLastFiredAt(String path, Instant lastFiredAt) {
        update(path, state -> state.lastFiredAt = lastFiredAt);
    }

    public void setConditionTrueSince(String path, Instant conditionTrueSince) {
        update(path, state -> state.conditionTrueSince = conditionTrueSince);
    }

    public void clearConditionTrueSince(String path) {
        setConditionTrueSince(path, null);
    }

    public void setLatchedActive(String path, boolean latchedActive) {
        update(path, state -> state.latchedActive = latchedActive);
    }

    public void setDeactivateTrueSince(String path, Instant deactivateTrueSince) {
        update(path, state -> state.deactivateTrueSince = deactivateTrueSince);
    }

    public void clearDeactivateTrueSince(String path) {
        setDeactivateTrueSince(path, null);
    }

    public void reset(String path) {
        update(path, state -> {
            state.lastConditionMet = false;
            state.lastWatchValue = null;
            state.lastFiredAt = null;
            state.conditionTrueSince = null;
            state.latchedActive = false;
            state.deactivateTrueSince = null;
        });
    }

    public void remove(String path) {
        states.remove(path);
    }

    public AlertRuleRuntimeState snapshotForPersist(String path) {
        MutableState state = states.get(path);
        if (state == null) {
            return AlertRuleRuntimeState.empty();
        }
        return state.snapshot();
    }

    public boolean isDirty(String path) {
        MutableState state = states.get(path);
        return state != null && state.isDirty();
    }

    /** A write made after {@code persisted} was taken keeps the rule dirty for the next flush. */
    public void markClean(String path, AlertRuleRuntimeState persisted) {
        MutableState state = states.get(path);
        if (state != null) {
            state.markCleanIfUnchanged(persisted);
        }
    }

    public List<String> drainDirtyPaths() {
        List<String> dirty = new ArrayList<>();
        for (var entry : states.entrySet()) {
            if (entry.getValue().isDirty()) {
                dirty.add(entry.getKey());
            }
        }
        return dirty;
    }

    private void update(String path, Consumer<MutableState> change) {
        states.computeIfAbsent(path, ignored -> new MutableState()).update(change);
    }

    private MutableState ensureLoaded(String path, PlatformObject node) {
        return states.computeIfAbsent(path, ignored -> fromNode(node));
    }

    private static MutableState fromNode(PlatformObject node) {
        AlertRuleRuntimeState loaded = AlertRuleRuntimeState.fromNode(node);
        MutableState state = new MutableState();
        state.lastConditionMet = loaded.lastConditionMet();
        state.lastWatchValue = loaded.lastWatchValue();
        state.lastFiredAt = loaded.lastFiredAt();
        state.conditionTrueSince = loaded.conditionTrueSince();
        state.latchedActive = loaded.latchedActive();
        state.deactivateTrueSince = loaded.deactivateTrueSince();
        state.dirty = false;
        return state;
    }
}
