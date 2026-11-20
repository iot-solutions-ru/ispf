package com.ispf.server.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.List;

/**
 * Local file buffer for MSSQL connectivity loss. Off unless the bean is created.
 * Does not ack a sample as stored in SQL. Does not replay automatically (no unique key yet).
 * Quota does not delete older lines.
 */
public final class MssqlJdbcHistoryBuffer {

    private final Path file;
    private final long maxBytes;

    public MssqlJdbcHistoryBuffer(Path directory, long maxBytes) {
        this.file = directory.resolve("buffer.log");
        this.maxBytes = maxBytes;
    }

    public static boolean isConnectivity(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            String state = current.getSQLState();
            if (state != null && state.startsWith("08")) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    public synchronized void accept(List<VariableHistoryWriteRecord> records) throws IOException {
        if (records == null || records.isEmpty()) {
            return;
        }
        Path directory = file.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        long used = Files.exists(file) ? Files.size(file) : 0L;
        StringBuilder body = new StringBuilder();
        for (VariableHistoryWriteRecord record : records) {
            body.append(record.objectPath()).append('\t')
                    .append(record.variableName()).append('\t')
                    .append(record.fieldName()).append('\t')
                    .append(record.sampledAt().toEpochMilli()).append('\t')
                    .append(record.valueDouble() == null ? "" : record.valueDouble())
                    .append('\n');
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        if (used + bytes.length > maxBytes) {
            throw new HistorianSpoolQuotaException(used, maxBytes);
        }
        Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public Path file() {
        return file;
    }
}
