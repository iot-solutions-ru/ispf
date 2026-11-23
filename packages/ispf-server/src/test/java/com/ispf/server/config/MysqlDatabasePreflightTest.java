package com.ispf.server.config;

import com.ispf.server.relational.MysqlDialect;
import com.ispf.server.relational.PostgreSqlDialect;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlDatabasePreflightTest {

    @Test
    void acceptsReadCommittedAndBinaryCollation() {
        assertThatCode(() -> MysqlDatabasePreflight.assertReady("READ-COMMITTED", "utf8mb4_0900_bin"))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesRepeatableRead() {
        assertThatThrownBy(() -> MysqlDatabasePreflight.assertReady("REPEATABLE-READ", "utf8mb4_0900_bin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ-COMMITTED")
                .hasMessageContaining("REPEATABLE-READ");
    }

    @Test
    void refusesCaseInsensitiveOrPadSpaceCollation() {
        assertThatThrownBy(() -> MysqlDatabasePreflight.assertReady("READ-COMMITTED", "utf8mb4_0900_ai_ci"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("utf8mb4_0900_bin");
        assertThatThrownBy(() -> MysqlDatabasePreflight.assertReady("READ-COMMITTED", "utf8mb4_bin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("utf8mb4_0900_bin");
        assertThatThrownBy(() -> MysqlDatabasePreflight.assertReady("READ-COMMITTED", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runnerChecksOnlyMysqlSingleEngine() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MysqlRelationalProperties b1 = new MysqlRelationalProperties();
        new MysqlDatabasePreflightRunner(new MysqlDialect(), b1, jdbc).assertDatabaseReady();

        MysqlRelationalProperties singleEngine = new MysqlRelationalProperties();
        singleEngine.setSingleEngine(true);
        new MysqlDatabasePreflightRunner(new PostgreSqlDialect(), singleEngine, jdbc).assertDatabaseReady();

        verify(jdbc, never()).queryForObject(anyString(), eq(String.class));
    }

    @Test
    void runnerRefusesRepeatableReadOnSingleEngine() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("transaction_isolation"), eq(String.class))).thenReturn("REPEATABLE-READ");
        when(jdbc.queryForObject(contains("default_collation_name"), eq(String.class))).thenReturn("utf8mb4_0900_bin");
        MysqlRelationalProperties singleEngine = new MysqlRelationalProperties();
        singleEngine.setSingleEngine(true);

        assertThatThrownBy(() -> new MysqlDatabasePreflightRunner(new MysqlDialect(), singleEngine, jdbc)
                .assertDatabaseReady())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ-COMMITTED");
    }

    @Test
    void runnerPassesOnTheSignedCell() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("transaction_isolation"), eq(String.class))).thenReturn("READ-COMMITTED");
        when(jdbc.queryForObject(contains("default_collation_name"), eq(String.class))).thenReturn("utf8mb4_0900_bin");
        MysqlRelationalProperties singleEngine = new MysqlRelationalProperties();
        singleEngine.setSingleEngine(true);

        assertThatCode(() -> new MysqlDatabasePreflightRunner(new MysqlDialect(), singleEngine, jdbc)
                .assertDatabaseReady())
                .doesNotThrowAnyException();
    }
}
