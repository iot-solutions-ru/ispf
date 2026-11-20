package com.ispf.server.relational;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Binds and reads instants as UTC so a non-UTC JVM does not shift {@code DATETIME2} / {@code TIMESTAMP} values.
 */
public final class JdbcUtcTimestamps {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private JdbcUtcTimestamps() {
    }

    public static void bind(PreparedStatement statement, int index, Instant instant) throws SQLException {
        Calendar utc = Calendar.getInstance(UTC);
        statement.setTimestamp(index, Timestamp.from(instant), utc);
    }

    /** Null-safe read of a timezone-less timestamp column written via {@link #bind}. */
    public static Instant read(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column, Calendar.getInstance(UTC));
        return timestamp == null ? null : timestamp.toInstant();
    }
}
