package com.ispf.server.history;

import java.time.Instant;
import java.util.List;

/**
 * Persistent index of primary ranges that still need a ClickHouse copy.
 */
public interface HistorianCatchUpRangeStore {

    void cover(String seriesKey, Instant from, Instant to);

    List<Range> list();

    void remove(String seriesKey, Instant from, Instant to);

    /**
     * Insert a span without merging it into a neighbour. Used to keep the part of a
     * shared range this node did not hold.
     */
    default void putExact(String seriesKey, Instant from, Instant to) {
        cover(seriesKey, from, to);
    }

    record Range(String seriesKey, Instant from, Instant to) {}
}
