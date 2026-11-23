package com.ispf.server.event;

import com.ispf.server.history.TimescaleHypertableInitializer;
import com.ispf.server.relational.MssqlDialect;
import com.ispf.server.relational.MysqlDialect;
import com.ispf.server.relational.RelationalDialect;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MySQL {@code DELETE … LIMIT} and MSSQL {@code DELETE TOP} purge batches, run on H2 in the matching
 * compatibility mode. The live MySQL run is {@code MysqlHistoryBucketTckTest}.
 */
class JdbcEventJournalBatchedPurgeTest {

    private static final Instant CUTOFF = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void mysqlPurgeDeletesEveryOldRowAcrossBatches() {
        Fixture fixture = fixture("MySQL", new MysqlDialect());
        fixture.seed("root.a.dev", 12_001, CUTOFF.minusSeconds(86_400));
        fixture.seed("root.a.dev", 5, CUTOFF.plusSeconds(60));

        long deleted = fixture.store.deleteOlderThan(CUTOFF, null);

        assertThat(deleted).isEqualTo(12_001);
        assertThat(fixture.count()).isEqualTo(5);
        verify(fixture.counter).recordDeleted(12_001);
    }

    @Test
    void mysqlPurgeStopsAfterAnExactlyFullLastBatch() {
        Fixture fixture = fixture("MySQL", new MysqlDialect());
        fixture.seed("root.a.dev", 10_000, CUTOFF.minusSeconds(3_600));

        assertThat(fixture.store.deleteOlderThan(CUTOFF, null)).isEqualTo(10_000);
        assertThat(fixture.count()).isZero();
    }

    @Test
    void mysqlScopedPurgeKeepsOtherObjects() {
        Fixture fixture = fixture("MySQL", new MysqlDialect());
        fixture.seed("root.a", 3, CUTOFF.minusSeconds(60));
        fixture.seed("root.a.child", 6_000, CUTOFF.minusSeconds(60));
        fixture.seed("root.ab", 4, CUTOFF.minusSeconds(60));
        fixture.seed("root.b", 7, CUTOFF.minusSeconds(60));

        long deleted = fixture.store.deleteOlderThan(CUTOFF, "root.a");

        assertThat(deleted).isEqualTo(6_003);
        assertThat(fixture.count()).isEqualTo(11);
    }

    @Test
    void mssqlPurgeKeepsDeleteTopBatches() {
        Fixture fixture = fixture("MSSQLServer", new MssqlDialect());
        fixture.seed("root.a.dev", 7_500, CUTOFF.minusSeconds(86_400));
        fixture.seed("root.b", 2, CUTOFF.minusSeconds(86_400));
        fixture.seed("root.a.dev", 3, CUTOFF.plusSeconds(60));

        assertThat(fixture.store.deleteOlderThan(CUTOFF, "root.a")).isEqualTo(7_500);
        assertThat(fixture.count()).isEqualTo(5);
        assertThat(fixture.store.deleteOlderThan(CUTOFF, null)).isEqualTo(2);
        assertThat(fixture.count()).isEqualTo(3);
    }

    private static Fixture fixture(String h2Mode, RelationalDialect dialect) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:event-purge-" + UUID.randomUUID() + ";MODE=" + h2Mode + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE event_history (
                    id VARCHAR(64) PRIMARY KEY,
                    object_path VARCHAR(512) NOT NULL,
                    event_name VARCHAR(128) NOT NULL,
                    level VARCHAR(32),
                    payload_json CLOB,
                    occurred_at TIMESTAMP NOT NULL
                )
                """);
        TimescaleHypertableInitializer timescale = mock(TimescaleHypertableInitializer.class);
        when(timescale.isEventHistoryTimescaleActive()).thenReturn(false);
        EventHistoryRecordCounter counter = mock(EventHistoryRecordCounter.class);
        JdbcEventJournalStore store = new JdbcEventJournalStore(
                jdbc, counter, timescale, dialect, new DataSourceTransactionManager(dataSource));
        return new Fixture(jdbc, store, counter);
    }

    private record Fixture(JdbcTemplate jdbc, JdbcEventJournalStore store, EventHistoryRecordCounter counter) {

        void seed(String objectPath, int rows, Instant occurredAt) {
            List<EventJournalRecord> batch = new ArrayList<>(rows);
            for (int i = 0; i < rows; i++) {
                batch.add(new EventJournalRecord(
                        UUID.randomUUID().toString(), objectPath, "purge", "INFO", "{}", occurredAt));
            }
            store.appendBatch(batch);
        }

        long count() {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM event_history", Long.class);
            return count == null ? 0 : count;
        }
    }
}
