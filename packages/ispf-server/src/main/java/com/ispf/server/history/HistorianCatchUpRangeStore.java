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

    record Range(String seriesKey, Instant from, Instant to) {}
}
