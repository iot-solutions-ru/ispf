package com.ispf.server.alert;

import com.ispf.core.object.PlatformObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertRuleRuntimeStore {

    private static final class MutableState {
        Boolean lastConditionMet;
        Double lastWatchValue;
        Instant lastFiredAt;
        Instant conditionTrueSince;
        boolean dirty;
    }

    private final ConcurrentHashMap<String, MutableState> states = new ConcurrentHashMap<>();

    public AlertRuleRuntimeState snapshot(String path, PlatformObject node) {
        return toSnapshot(ensureLoaded(path, node));
    }

    public Double getLastWatchValue(String path, PlatformObject node) {
        return snapshot(path, node).lastWatchValue();
    }

    public void setLastConditionMet(String path, boolean lastConditionMet) {
        MutableState state = states.computeIfAbsent(path, ignored -> new MutableState());
        state.lastConditionMet = lastConditionMet;
        state.dirty = true;
    }

    public void setLastWatchValue(String path, Double value) {
        MutableState state = states.computeIfAbsent(path, ignored -> new MutableState());
        state.lastWatchValue = value;
        state.dirty = true;
    }

    public void setLastFiredAt(String path, Instant lastFiredAt) {
        MutableState state = states.computeIfAbsent(path, ignored -> new MutableState());
        state.lastFiredAt = lastFiredAt;
        state.dirty = true;
    }

    public void setConditionTrueSince(String path, Instant conditionTrueSince) {
        MutableState state = states.computeIfAbsent(path, ignored -> new MutableState());
        state.conditionTrueSince = conditionTrueSince;
        state.dirty = true;
    }

    public void clearConditionTrueSince(String path) {
        setConditionTrueSince(path, null);
    }

    public void reset(String path) {
        MutableState state = states.computeIfAbsent(path, ignored -> new MutableState());
        state.lastConditionMet = false;
        state.lastWatchValue = null;
        state.lastFiredAt = null;
        state.conditionTrueSince = null;
        state.dirty = true;
    }

    public void remove(String path) {
        states.remove(path);
    }

    public AlertRuleRuntimeState snapshotForPersist(String path) {
        MutableState state = states.get(path);
        if (state == null) {
            return AlertRuleRuntimeState.empty();
        }
        return toSnapshot(state);
    }

    public boolean isDirty(String path) {
        MutableState state = states.get(path);
        return state != null && state.dirty;
    }

    public void markClean(String path) {
        MutableState state = states.get(path);
        if (state != null) {
            state.dirty = false;
        }
    }

    public List<String> drainDirtyPaths() {
        List<String> dirty = new ArrayList<>();
        for (var entry : states.entrySet()) {
            if (entry.getValue().dirty) {
                dirty.add(entry.getKey());
            }
        }
        return dirty;
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
        state.dirty = false;
        return state;
    }

    private static AlertRuleRuntimeState toSnapshot(MutableState state) {
        return new AlertRuleRuntimeState(
                state.lastConditionMet,
                state.lastWatchValue,
                state.lastFiredAt,
                state.conditionTrueSince
        );
    }
}
