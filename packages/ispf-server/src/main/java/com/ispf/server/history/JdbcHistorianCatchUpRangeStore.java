package com.ispf.server.history;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcHistorianCatchUpRangeStore implements HistorianCatchUpRangeStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcHistorianCatchUpRangeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void cover(String seriesKey, Instant from, Instant to) {
        Instant start = from.isBefore(to) ? from : to;
        Instant end = from.isBefore(to) ? to : from;
        List<HistorianCatchUpRangeStore.Range> overlapping = jdbcTemplate.query(
                """
                SELECT series_key, from_ts, to_ts FROM historian_catchup_ranges
                WHERE series_key = ? AND from_ts <= ? AND to_ts >= ?
                """,
                (rs, row) -> new Range(
                        rs.getString(1),
                        rs.getTimestamp(2).toInstant(),
                        rs.getTimestamp(3).toInstant()
                ),
                seriesKey,
                Timestamp.from(end),
                Timestamp.from(start)
        );
        Instant mergedFrom = start;
        Instant mergedTo = end;
        for (Range range : overlapping) {
            if (range.from().isBefore(mergedFrom)) {
                mergedFrom = range.from();
            }
            if (range.to().isAfter(mergedTo)) {
                mergedTo = range.to();
            }
        }
        jdbcTemplate.update("DELETE FROM historian_catchup_ranges WHERE series_key = ? AND from_ts <= ? AND to_ts >= ?",
                seriesKey, Timestamp.from(end), Timestamp.from(start));
        jdbcTemplate.update(
                "INSERT INTO historian_catchup_ranges (series_key, from_ts, to_ts) VALUES (?, ?, ?)",
                seriesKey, Timestamp.from(mergedFrom), Timestamp.from(mergedTo)
        );
    }

    @Override
    public List<Range> list() {
        return jdbcTemplate.query(
                "SELECT series_key, from_ts, to_ts FROM historian_catchup_ranges ORDER BY from_ts",
                (rs, row) -> new Range(
                        rs.getString(1),
                        rs.getTimestamp(2).toInstant(),
                        rs.getTimestamp(3).toInstant()
                )
        );
    }

    @Override
    public void remove(String seriesKey, Instant from, Instant to) {
        jdbcTemplate.update(
                "DELETE FROM historian_catchup_ranges WHERE series_key = ? AND from_ts = ? AND to_ts = ?",
                seriesKey, Timestamp.from(from), Timestamp.from(to)
        );
    }
}
