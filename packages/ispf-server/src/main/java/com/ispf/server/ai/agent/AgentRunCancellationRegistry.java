package com.ispf.server.ai.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-flight agent runs: cooperative cancel + live step progress for UI polling.
 */
@Component
public class AgentRunCancellationRegistry {

    private static final class RunState {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile String userMessage;
        final List<Map<String, Object>> steps = Collections.synchronizedList(new ArrayList<>());
    }

    private final ConcurrentHashMap<String, RunState> runsBySession = new ConcurrentHashMap<>();

    public RunHandle start(String sessionId, String userMessage) {
        RunState state = new RunState();
        state.userMessage = userMessage != null ? userMessage : "";
        runsBySession.put(sessionId, state);
        return () -> runsBySession.remove(sessionId);
    }

    public void cancel(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RunState state = runsBySession.get(sessionId);
        if (state != null) {
            state.cancelled.set(true);
        }
    }

    public boolean isCancelled(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        RunState state = runsBySession.get(sessionId);
        return state != null && state.cancelled.get();
    }

    public boolean isRunning(String sessionId) {
        return sessionId != null && runsBySession.containsKey(sessionId);
    }

    public void recordStep(String sessionId, Map<String, Object> step) {
        if (sessionId == null || step == null) {
            return;
        }
        RunState state = runsBySession.get(sessionId);
        if (state != null) {
            state.steps.add(step);
        }
    }

    public Map<String, Object> progress(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Map.of("running", false);
        }
        RunState state = runsBySession.get(sessionId);
        if (state == null) {
            return Map.of("running", false);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("running", true);
        map.put("sessionId", sessionId);
        map.put("userMessage", state.userMessage);
        map.put("steps", List.copyOf(state.steps));
        map.put("stepsCompleted", state.steps.size());
        return map;
    }

    @FunctionalInterface
    public interface RunHandle extends AutoCloseable {
        @Override
        void close();
    }
}
