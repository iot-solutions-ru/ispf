package com.ispf.server.relational.store;

import com.ispf.server.relational.MysqlDialect;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VariableSampleBucketQueryTest {

    @Test
    void mysqlBindsEightParametersInSqlOrderWithUtcTimestamps() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MysqlDialect dialect = new MysqlDialect();
        ArgumentCaptor<PreparedStatementSetter> setter = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        when(jdbc.query(eq(dialect.variableSampleBucketAggregateSql()), setter.capture(), any(RowMapper.class)))
                .thenReturn(List.of());
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");

        new VariableSampleBucketQuery(jdbc, dialect)
                .aggregateBuckets("root.device", "temp", "value", from, to, 300, 200);

        PreparedStatement statement = mock(PreparedStatement.class);
        setter.getValue().setValues(statement);
        var order = inOrder(statement);
        order.verify(statement).setLong(1, 300);
        order.verify(statement).setLong(2, 300);
        order.verify(statement).setString(3, "root.device");
        order.verify(statement).setString(4, "temp");
        order.verify(statement).setString(5, "value");
        order.verify(statement).setTimestamp(eq(6), eq(Timestamp.from(from)), argThat(VariableSampleBucketQueryTest::utc));
        order.verify(statement).setTimestamp(eq(7), eq(Timestamp.from(to)), argThat(VariableSampleBucketQueryTest::utc));
        order.verify(statement).setInt(8, 200);
        assertThat(dialect.variableSampleBucketAggregateSql().split("\\?", -1).length - 1).isEqualTo(8);
    }

    private static boolean utc(Calendar calendar) {
        return calendar != null && "UTC".equals(calendar.getTimeZone().getID());
    }
}
