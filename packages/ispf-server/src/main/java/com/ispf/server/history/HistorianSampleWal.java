package com.ispf.server.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only disk journal of historian samples that reached primary storage
 * but not ClickHouse. Oldest lines are dropped when {@code maxBytes} is exceeded.
 */
public final class HistorianSampleWal {

    private final Path file;
    private final long maxBytes;

    public HistorianSampleWal(Path directory, long maxBytes) {
        this.file = directory.resolve("samples.wal");
        this.maxBytes = Math.max(4096L, maxBytes);
        try {
            Files.createDirectories(directory);
            if (!Files.exists(file)) {
                Files.createFile(file);
            }
            enforceQuota();
        } catch (IOException ex) {
            throw new IllegalStateException("historian WAL directory is not writable: " + directory, ex);
        }
    }

    public synchronized void append(List<VariableHistoryWriteRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        StringBuilder block = new StringBuilder();
        for (VariableHistoryWriteRecord record : records) {
            block.append(encode(record)).append('\n');
        }
        try {
            Files.writeString(file, block, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            enforceQuota();
        } catch (IOException ex) {
            throw new IllegalStateException("historian WAL append failed", ex);
        }
    }

    public synchronized List<VariableHistoryWriteRecord> readAll() {
        try {
            if (!Files.exists(file)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<VariableHistoryWriteRecord> records = new ArrayList<>();
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    records.add(decode(line));
                } catch (RuntimeException ex) {
                    // A torn tail line must not stall every later catch-up tick.
                }
            }
            return records;
        } catch (IOException ex) {
            throw new IllegalStateException("historian WAL read failed", ex);
        }
    }

    public synchronized void rewrite(List<VariableHistoryWriteRecord> remaining) {
        try {
            StringBuilder block = new StringBuilder();
            for (VariableHistoryWriteRecord record : remaining) {
                block.append(encode(record)).append('\n');
            }
            Files.writeString(file, block, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException ex) {
            throw new IllegalStateException("historian WAL rewrite failed", ex);
        }
    }

    private void enforceQuota() throws IOException {
        long size = Files.size(file);
        if (size <= maxBytes) {
            return;
        }
        byte[] raw = Files.readAllBytes(file);
        List<byte[]> lines = splitLines(raw);
        int keepFrom = lines.size();
        long keptBytes = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            long lineBytes = lines.get(i).length + 1L;
            if (lineBytes > maxBytes || keptBytes + lineBytes > maxBytes) {
                break;
            }
            keptBytes += lineBytes;
            keepFrom = i;
        }
        StringBuilder block = new StringBuilder();
        for (int i = keepFrom; i < lines.size(); i++) {
            block.append(new String(lines.get(i), StandardCharsets.UTF_8)).append('\n');
        }
        Files.writeString(
                file,
                block,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE
        );
    }

    private static List<byte[]> splitLines(byte[] raw) {
        List<byte[]> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == '\n') {
                int end = i;
                if (end > start && raw[end - 1] == '\r') {
                    end--;
                }
                lines.add(java.util.Arrays.copyOfRange(raw, start, end));
                start = i + 1;
            }
        }
        if (start < raw.length) {
            lines.add(java.util.Arrays.copyOfRange(raw, start, raw.length));
        }
        return lines;
    }

    static String encode(VariableHistoryWriteRecord record) {
        return record.sampledAt().toEpochMilli()
                + "\t" + escape(record.objectPath())
                + "\t" + escape(record.variableName())
                + "\t" + escape(record.fieldName())
                + "\t" + (record.valueDouble() == null ? "" : record.valueDouble())
                + "\t" + escape(record.valueText())
                + "\t" + escape(record.quality())
                + "\t" + (record.observedAt() == null ? "" : record.observedAt().toEpochMilli());
    }

    static VariableHistoryWriteRecord decode(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 8) {
            throw new IllegalArgumentException("truncated historian WAL line");
        }
        Instant sampledAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
        Double value = parts[4].isEmpty() ? null : Double.valueOf(parts[4]);
        Instant observedAt = parts[7].isEmpty() ? sampledAt : Instant.ofEpochMilli(Long.parseLong(parts[7]));
        return new VariableHistoryWriteRecord(
                unescape(parts[1]),
                unescape(parts[2]),
                unescape(parts[3]),
                sampledAt,
                observedAt,
                value,
                unescape(parts[5]),
                unescape(parts[6])
        );
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                out.append(switch (next) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> next;
                });
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
