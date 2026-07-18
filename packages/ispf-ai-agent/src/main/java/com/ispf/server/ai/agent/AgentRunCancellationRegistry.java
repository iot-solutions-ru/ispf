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
        volatile long startedAtMs = System.currentTimeMillis();
        volatile long lastProgressAtMs = System.currentTimeMillis();
        volatile String userMessage;
        volatile Map<String, Object> planState = Map.of();
        final List<Map<String, Object>> steps = Collections.synchronizedList(new ArrayList<>());
    }

    private static final class CompletedSnapshot {
        final long completedAtMs;
        final Map<String, Object> progress;

        CompletedSnapshot(long completedAtMs, Map<String, Object> progress) {
            this.completedAtMs = completedAtMs;
            this.progress = progress;
        }
    }

    /** Runs with no step progress longer than this are treated as stale (orphaned async turn). */
    private static final long STALE_IDLE_MS = 15 * 60 * 1000L;
    /** Hard cap — never block a session longer than this. */
    private static final long STALE_MAX_MS = 2 * 60 * 60 * 1000L;
    /** Keep last progress visible after close so the UI can settle without F5. */
    private static final long COMPLETED_TTL_MS = 60_000L;

    private final ConcurrentHashMap<String, RunState> runsBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletedSnapshot> completedBySession = new ConcurrentHashMap<>();

    public void touch(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RunState state = runsBySession.get(sessionId);
        if (state != null) {
            state.lastProgressAtMs = System.currentTimeMillis();
        }
    }

    public void updateUserMessage(String sessionId, String userMessage) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        RunState state = runsBySession.get(sessionId);
        if (state != null && userMessage != null && !userMessage.isBlank()) {
            state.userMessage = userMessage;
            state.lastProgressAtMs = System.currentTimeMillis();
        }
    }

    public RunHandle start(String sessionId, String userMessage, Map<String, Object> planState) {
        clearStaleRun(sessionId);
        completedBySession.remove(sessionId);
        RunState state = new RunState();
        state.userMessage = userMessage != null ? userMessage : "";
        state.planState = planState != null ? new LinkedHashMap<>(planState) : Map.of();
        runsBySession.put(sessionId, state);
        return () -> closeRun(sessionId, state);
    }

    public void syncPlanState(String sessionId, Map<String, Object> planState) {
        if (sessionId == null) {
            return;
        }
        RunState state = runsBySession.get(sessionId);
        if (state != null) {
            state.planState = planState != null ? new LinkedHashMap<>(planState) : Map.of();
            state.lastProgressAtMs = System.currentTimeMillis();
        }
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
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        RunState state = runsBySession.get(sessionId);
        if (state == null) {
            return false;
        }
        if (isStale(state)) {
            runsBySession.remove(sessionId, state);
            return false;
        }
        return true;
    }

    /** Drop orphaned in-memory run so a new turn can start (page refresh during async turn). */
    public boolean clearStaleRun(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        RunState state = runsBySession.get(sessionId);
        if (state != null && isStale(state)) {
            closeRun(sessionId, state);
            return true;
        }
        return false;
    }

    public void forceClear(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            RunState state = runsBySession.get(sessionId);
            if (state != null) {
                closeRun(sessionId, state);
            } else {
                completedBySession.remove(sessionId);
            }
        }
    }

    private static boolean isStale(RunState state) {
        long now = System.currentTimeMillis();
        if (now - state.startedAtMs > STALE_MAX_MS) {
            return true;
        }
        return now - state.lastProgressAtMs > STALE_IDLE_MS;
    }

    public void recordStep(String sessionId, Map<String, Object> step) {
        if (sessionId == null || step == null) {
            return;
        }
        RunState state = runsBySession.get(sessionId);
        if (state != null) {
            state.steps.add(step);
            state.lastProgressAtMs = System.currentTimeMillis();
        }
    }

    public Map<String, Object> progress(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Map.of("running", false);
        }
        RunState state = runsBySession.get(sessionId);
        if (state != null) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("running", true);
            map.put("sessionId", sessionId);
            map.put("userMessage", state.userMessage);
            map.put("steps", List.copyOf(state.steps));
            map.put("stepsCompleted", state.steps.size());
            if (state.planState != null && !state.planState.isEmpty()) {
                map.put("planState", state.planState);
            }
            return map;
        }
        CompletedSnapshot done = completedBySession.get(sessionId);
        if (done != null) {
            long age = System.currentTimeMillis() - done.completedAtMs;
            if (age <= COMPLETED_TTL_MS) {
                return done.progress;
            }
            completedBySession.remove(sessionId, done);
        }
        return Map.of("running", false);
    }

    private void closeRun(String sessionId, RunState state) {
        if (!runsBySession.remove(sessionId, state)) {
            return;
        }
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("running", false);
        snap.put("completed", true);
        snap.put("sessionId", sessionId);
        snap.put("userMessage", state.userMessage);
        snap.put("steps", List.copyOf(state.steps));
        snap.put("stepsCompleted", state.steps.size());
        if (state.planState != null && !state.planState.isEmpty()) {
            snap.put("planState", state.planState);
        }
        completedBySession.put(sessionId, new CompletedSnapshot(System.currentTimeMillis(), snap));
    }

    @FunctionalInterface
    public interface RunHandle extends AutoCloseable {
        @Override
        void close();
    }
}
