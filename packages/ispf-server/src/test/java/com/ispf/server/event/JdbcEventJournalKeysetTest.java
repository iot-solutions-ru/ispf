package com.ispf.server.event;

import com.ispf.server.history.TimescaleHypertableInitializer;
import com.ispf.server.relational.H2Dialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcEventJournalKeysetTest {

    private TimeZone previousZone;
    private JdbcEventJournalStore store;

    @BeforeEach
    void setUp() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:event-keyset-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
        store = new JdbcEventJournalStore(jdbc, counter, timescale, new H2Dialect());
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(previousZone);
    }

    @Test
    void keysetReturnsOlderPage() {
        Instant t1 = Instant.parse("2026-01-01T00:00:01Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:02Z");
        Instant t3 = Instant.parse("2026-01-01T00:00:03Z");
        store.appendOne(new EventJournalRecord("a", "root.d", "alarm", "WARN", "{}", t1));
        store.appendOne(new EventJournalRecord("b", "root.d", "alarm", "WARN", "{}", t2));
        store.appendOne(new EventJournalRecord("c", "root.d", "alarm", "WARN", "{}", t3));

        List<EventJournalRecord> page = store.queryRecentBefore("root.d", t3, "c", 10);
        assertThat(page).extracting(EventJournalRecord::id).containsExactly("b", "a");
    }

    @Test
    void chainedPagesUseReturnedCursorUnderNonUtcJvm() {
        Instant t1 = Instant.parse("2026-01-01T00:00:01Z");
        Instant t2 = Instant.parse("2026-01-01T00:00:02Z");
        Instant t3 = Instant.parse("2026-01-01T00:00:03Z");
        store.appendOne(new EventJournalRecord("a", "root.d", "alarm", "WARN", "{}", t1));
        store.appendOne(new EventJournalRecord("b", "root.d", "alarm", "WARN", "{}", t2));
        store.appendOne(new EventJournalRecord("c", "root.d", "alarm", "WARN", "{}", t3));

        List<EventJournalRecord> page1 = store.queryRecent("root.d", 1);
        assertThat(page1).extracting(EventJournalRecord::id).containsExactly("c");
        assertThat(page1.getFirst().occurredAt()).isEqualTo(t3);

        List<EventJournalRecord> page2 = store.queryRecentBefore(
                "root.d", page1.getFirst().occurredAt(), page1.getFirst().id(), 10);
        assertThat(page2).extracting(EventJournalRecord::id).containsExactly("b", "a");
        assertThat(page2.getFirst().occurredAt()).isEqualTo(t2);
    }

    @Test
    void collidingTimestampsPaginateByIdDesc() {
        Instant same = Instant.parse("2026-01-01T00:00:05Z");
        store.appendOne(new EventJournalRecord("a", "root.d", "alarm", "WARN", "{}", same));
        store.appendOne(new EventJournalRecord("b", "root.d", "alarm", "WARN", "{}", same));
        store.appendOne(new EventJournalRecord("c", "root.d", "alarm", "WARN", "{}", same));

        List<EventJournalRecord> page1 = store.queryRecent("root.d", 2);
        assertThat(page1).extracting(EventJournalRecord::id).containsExactly("c", "b");

        List<EventJournalRecord> page2 = store.queryRecentBefore(
                "root.d", page1.get(1).occurredAt(), page1.get(1).id(), 10);
        assertThat(page2).extracting(EventJournalRecord::id).containsExactly("a");
    }
}
