package com.ispf.server.history;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.CRC32;

/**
 * Segmented store-and-forward log for sole-store ClickHouse.
 * A frame is durable before ClickHouse is called. Quota does not delete old samples.
 */
public final class HistorianDiskSpool {

    static final int MAGIC = 0x48535031;
    private static final int HEADER = 20;

    private final Path directory;
    private final long maxBytes;
    private final long segmentBytes;
    private final boolean fsync;
    private final ObjectMapper objectMapper;
    private final TreeSet<Long> ackedAbove = new TreeSet<>();
    private long ackedThrough;
    private long nextSeq = 1;
    private int segmentIndex = 1;
    private FileChannel channel;
    private long segmentSize;
    private long usedBytes;
    private int unackedRecords;
    private long oldestUnackedMillis;
    private long replayedBatches;
    private long errors;
    private boolean quotaHit;

    public HistorianDiskSpool(Path directory, long maxBytes, long segmentBytes, boolean fsync, ObjectMapper objectMapper) {
        this.directory = directory;
        this.maxBytes = maxBytes;
        this.segmentBytes = Math.max(HEADER + 64, segmentBytes);
        this.fsync = fsync;
        this.objectMapper = objectMapper;
    }

    public synchronized void open() throws IOException {
        Files.createDirectories(directory);
        loadCheckpoint();
        List<Path> segments = listSegments();
        if (!segments.isEmpty()) {
            String name = segments.getLast().getFileName().toString();
            segmentIndex = Integer.parseInt(name.substring(0, name.indexOf('.')));
        }
        usedBytes = 0;
        for (Path segment : segments) {
            usedBytes += Files.size(segment);
        }
        truncateTornTail(segments);
        recoverNextSeq(segments);
        openSegment(segmentIndex, true);
    }

    public synchronized String append(List<VariableHistoryWriteRecord> records) throws IOException {
        if (records == null || records.isEmpty()) {
            return null;
        }
        byte[] payload = encode(records);
        int frame = HEADER + payload.length;
        if (usedBytes + frame > maxBytes) {
            quotaHit = true;
            throw new HistorianSpoolQuotaException(usedBytes, maxBytes);
        }
        if (channel == null || segmentSize + frame > segmentBytes) {
            rotate();
        }
        long seq = nextSeq++;
        ByteBuffer header = ByteBuffer.allocate(HEADER);
        header.putInt(MAGIC);
        header.putInt(payload.length);
        header.putInt(crc(payload));
        header.putLong(System.currentTimeMillis());
        header.flip();
        channel.write(header);
        channel.write(ByteBuffer.wrap(payload));
        if (fsync) {
            channel.force(true);
        }
        segmentSize += frame;
        usedBytes += frame;
        unackedRecords += records.size();
        if (oldestUnackedMillis == 0) {
            oldestUnackedMillis = System.currentTimeMillis();
        }
        return Long.toString(seq);
    }

    public synchronized void ack(String batchId) throws IOException {
        if (batchId == null) {
            return;
        }
        long seq = Long.parseLong(batchId);
        if (seq <= ackedThrough) {
            return;
        }
        ackedAbove.add(seq);
        while (ackedAbove.contains(ackedThrough + 1)) {
            ackedAbove.remove(ackedThrough + 1);
            ackedThrough++;
        }
        writeCheckpoint();
    }

    public synchronized int replay(Replay sink, int limit) throws IOException {
        int copied = 0;
        for (Path segment : listSegments()) {
            try (FileChannel reader = FileChannel.open(segment, StandardOpenOption.READ)) {
                ByteBuffer header = ByteBuffer.allocate(HEADER);
                while (copied < limit) {
                    header.clear();
                    if (!readFully(reader, header)) {
                        break;
                    }
                    header.flip();
                    int magic = header.getInt();
                    int length = header.getInt();
                    int expected = header.getInt();
                    header.getLong();
                    if (magic != MAGIC || length < 0 || length > 32 * 1024 * 1024) {
                        break;
                    }
                    ByteBuffer body = ByteBuffer.allocate(length);
                    if (!readFully(reader, body)) {
                        break;
                    }
                    byte[] payload = body.array();
                    if (crc(payload) != expected) {
                        break;
                    }
                    Frame frame = decode(payload);
                    if (frame.seq() <= ackedThrough || ackedAbove.contains(frame.seq())) {
                        continue;
                    }
                    try {
                        sink.accept(Long.toString(frame.seq()), frame.records());
                        ack(Long.toString(frame.seq()));
                        copied++;
                        replayedBatches++;
                    } catch (RuntimeException ex) {
                        errors++;
                        return copied;
                    }
                }
            }
        }
        refreshUnacked();
        return copied;
    }

    public synchronized Stats stats() {
        long age = oldestUnackedMillis == 0 ? 0 : Math.max(0, System.currentTimeMillis() - oldestUnackedMillis);
        return new Stats(usedBytes, unackedRecords, age, replayedBatches, errors, quotaHit);
    }

    public synchronized void close() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    private void rotate() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
        if (segmentSize > 0) {
            segmentIndex++;
        }
        openSegment(segmentIndex, false);
    }

    private void openSegment(int index, boolean append) throws IOException {
        Path path = directory.resolve(String.format("%06d.seg", index));
        channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING
        );
        segmentSize = append ? channel.size() : 0;
        if (!append) {
            usedBytes += 0;
        }
    }

    private void truncateTornTail(List<Path> segments) throws IOException {
        if (segments.isEmpty()) {
            return;
        }
        Path last = segments.getLast();
        try (FileChannel reader = FileChannel.open(last, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long good = 0;
            ByteBuffer header = ByteBuffer.allocate(HEADER);
            while (true) {
                long mark = reader.position();
                header.clear();
                if (!readFully(reader, header)) {
                    break;
                }
                header.flip();
                int magic = header.getInt();
                int length = header.getInt();
                int expected = header.getInt();
                header.getLong();
                if (magic != MAGIC || length < 0) {
                    break;
                }
                ByteBuffer body = ByteBuffer.allocate(length);
                if (!readFully(reader, body) || crc(body.array()) != expected) {
                    break;
                }
                good = reader.position();
                if (reader.position() < mark) {
                    break;
                }
            }
            long size = reader.size();
            if (good < size) {
                reader.truncate(good);
                usedBytes -= size - good;
            }
        }
    }

    private void recoverNextSeq(List<Path> segments) throws IOException {
        long max = ackedThrough;
        for (Path segment : segments) {
            try (FileChannel reader = FileChannel.open(segment, StandardOpenOption.READ)) {
                ByteBuffer header = ByteBuffer.allocate(HEADER);
                while (true) {
                    header.clear();
                    if (!readFully(reader, header)) {
                        break;
                    }
                    header.flip();
                    if (header.getInt() != MAGIC) {
                        break;
                    }
                    int length = header.getInt();
                    int expected = header.getInt();
                    header.getLong();
                    ByteBuffer body = ByteBuffer.allocate(length);
                    if (!readFully(reader, body) || crc(body.array()) != expected) {
                        break;
                    }
                    Frame frame = decode(body.array());
                    max = Math.max(max, frame.seq());
                    if (frame.seq() > ackedThrough && !ackedAbove.contains(frame.seq())) {
                        unackedRecords += frame.records().size();
                    }
                }
            }
        }
        nextSeq = max + 1;
        if (unackedRecords > 0) {
            oldestUnackedMillis = System.currentTimeMillis();
        }
    }

    private void refreshUnacked() throws IOException {
        unackedRecords = 0;
        recoverNextSeq(listSegments());
        if (unackedRecords == 0) {
            oldestUnackedMillis = 0;
        }
    }

    private byte[] encode(List<VariableHistoryWriteRecord> records) throws IOException {
        StringBuilder body = new StringBuilder();
        body.append(nextSeq).append('\n');
        for (VariableHistoryWriteRecord record : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("objectPath", record.objectPath());
            row.put("variableName", record.variableName());
            row.put("fieldName", record.fieldName());
            row.put("sampledAt", record.sampledAt() == null ? null : record.sampledAt().toEpochMilli());
            row.put("observedAt", record.observedAt() == null ? null : record.observedAt().toEpochMilli());
            row.put("valueDouble", record.valueDouble());
            row.put("valueText", record.valueText());
            row.put("quality", record.quality());
            body.append(objectMapper.writeValueAsString(row)).append('\n');
        }
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Frame decode(byte[] payload) throws IOException {
        String text = new String(payload, StandardCharsets.UTF_8);
        int split = text.indexOf('\n');
        long seq = Long.parseLong(text.substring(0, split));
        List<VariableHistoryWriteRecord> records = new ArrayList<>();
        for (String line : text.substring(split + 1).split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            var node = objectMapper.readTree(line);
            records.add(new VariableHistoryWriteRecord(
                    node.path("objectPath").asText(),
                    node.path("variableName").asText(),
                    node.path("fieldName").asText(),
                    java.time.Instant.ofEpochMilli(node.path("sampledAt").asLong()),
                    java.time.Instant.ofEpochMilli(node.path("observedAt").asLong()),
                    node.path("valueDouble").isNull() ? null : node.path("valueDouble").asDouble(),
                    node.path("valueText").isNull() ? null : node.path("valueText").asText(null),
                    node.path("quality").asText(null)
            ));
        }
        return new Frame(seq, records);
    }

    private void loadCheckpoint() throws IOException {
        Path path = directory.resolve("checkpoint");
        if (!Files.exists(path)) {
            return;
        }
        List<String> lines = Files.readAllLines(path);
        if (!lines.isEmpty() && !lines.getFirst().isBlank()) {
            ackedThrough = Long.parseLong(lines.getFirst().trim());
        }
        if (lines.size() > 1 && !lines.get(1).isBlank()) {
            for (String part : lines.get(1).split(",")) {
                if (!part.isBlank()) {
                    ackedAbove.add(Long.parseLong(part.trim()));
                }
            }
        }
    }

    private void writeCheckpoint() throws IOException {
        String gaps = ackedAbove.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        Path path = directory.resolve("checkpoint");
        Files.writeString(path, ackedThrough + "\n" + gaps + "\n", StandardCharsets.UTF_8);
        if (fsync) {
            try (FileChannel checkpoint = FileChannel.open(path, StandardOpenOption.WRITE)) {
                checkpoint.force(true);
            }
        }
    }

    private List<Path> listSegments() throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".seg"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static boolean readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int crc(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        return (int) crc.getValue();
    }

    public record Stats(long bytes, int unackedRecords, long oldestAgeMillis, long replayedBatches, long errors, boolean quotaHit) {
    }

    public record Frame(long seq, List<VariableHistoryWriteRecord> records) {
    }

    @FunctionalInterface
    public interface Replay {
        void accept(String batchId, List<VariableHistoryWriteRecord> records);
    }
}
