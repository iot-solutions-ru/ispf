package com.ispf.server.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MssqlJdbcHistoryBufferTest {

    @TempDir
    Path directory;

    @Test
    void acceptsLinesUntilQuota() throws Exception {
        MssqlJdbcHistoryBuffer buffer = new MssqlJdbcHistoryBuffer(directory, 10_000);
        buffer.accept(List.of(sample()));
        assertThat(Files.readString(buffer.file())).contains("root.device");
    }

    @Test
    void quotaDoesNotDropOlderLines() {
        MssqlJdbcHistoryBuffer buffer = new MssqlJdbcHistoryBuffer(directory, 8);
        assertThatThrownBy(() -> buffer.accept(List.of(sample())))
                .isInstanceOf(HistorianSpoolQuotaException.class);
        assertThat(Files.exists(buffer.file())).isFalse();
    }

    @Test
    void connectivityIsSqlState08() {
        assertThat(MssqlJdbcHistoryBuffer.isConnectivity(new SQLException("down", "08S01"))).isTrue();
        assertThat(MssqlJdbcHistoryBuffer.isConnectivity(new SQLException("dup", "23000", 2627))).isFalse();
    }

    private static VariableHistoryWriteRecord sample() {
        return new VariableHistoryWriteRecord(
                "root.device", "temp", "value", Instant.EPOCH, Instant.EPOCH, 1.5, null, null);
    }
}
