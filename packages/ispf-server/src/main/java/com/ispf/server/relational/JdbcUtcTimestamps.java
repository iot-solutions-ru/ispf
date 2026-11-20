package com.ispf.server.relational;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Binds instants as UTC so a non-UTC JVM does not shift {@code DATETIME2} values.
 */
public final class JdbcUtcTimestamps {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private JdbcUtcTimestamps() {
    }

    public static void bind(PreparedStatement statement, int index, Instant instant) throws SQLException {
        Calendar utc = Calendar.getInstance(UTC);
        statement.setTimestamp(index, Timestamp.from(instant), utc);
    }
}
