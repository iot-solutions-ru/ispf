package com.ispf.server.websocket;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Bounded replay buffer for WebSocket reconnect deltas ({@code lastSeq} cursor).
 */
public final class ReplayCursor {

    public record ReplayItem(long seq, Object payload) {
    }

    private final int capacity;
    private final Deque<ReplayItem> buffer = new ArrayDeque<>();
    private long clientLastSeq;

    public ReplayCursor(long initialLastSeq, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.clientLastSeq = initialLastSeq;
        this.capacity = capacity;
    }

    /**
     * Client cursor advanced by {@link #deltaSince(long)}. A buffer shared by many sessions
     * should use {@link #newestSeq()} for the high-water mark; this value only remembers the
     * last caller.
     */
    public long lastSeq() {
        return clientLastSeq;
    }

    /** Oldest retained sequence, or null when the buffer is empty. */
    public Long oldestSeq() {
        ReplayItem first = buffer.peekFirst();
        return first == null ? null : first.seq();
    }

    /** Highest retained sequence, or 0 when the buffer is empty. */
    public long newestSeq() {
        ReplayItem last = buffer.peekLast();
        return last == null ? 0L : last.seq();
    }

    public void offer(long seq, Object payload) {
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be positive");
        }
        buffer.addLast(new ReplayItem(seq, payload));
        while (buffer.size() > capacity) {
            buffer.removeFirst();
        }
    }

    /**
     * Items with {@code seq} greater than {@code sinceSeq} that are still retained in the buffer.
     * Updates {@link #lastSeq()} to the highest returned sequence when non-empty.
     */
    public List<ReplayItem> deltaSince(long sinceSeq) {
        List<ReplayItem> deltas = new ArrayList<>();
        for (ReplayItem item : buffer) {
            if (item.seq() > sinceSeq) {
                deltas.add(item);
            }
        }
        if (!deltas.isEmpty()) {
            clientLastSeq = deltas.getLast().seq();
        }
        return List.copyOf(deltas);
    }
}
