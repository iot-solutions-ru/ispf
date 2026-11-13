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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * Segmented store-and-forward log for sole-store ClickHouse.
 * A frame is durable before ClickHouse is called. Quota does not delete old samples.
 */
public final class HistorianDiskSpool {

    static final int MAGIC = 0x48535031;
    private static final int HEADER = 20;
    static final int MAX_FRAME_BYTES = 32 * 1024 * 1024;
    private static final int REPLAY_PAYLOAD_BUDGET = 16 * 1024 * 1024;

    private final Path directory;
    private final long maxBytes;
    private final long segmentBytes;
    private final boolean fsync;
    private final ObjectMapper objectMapper;
    private final TreeSet<Long> ackedAbove = new TreeSet<>();
    private final Map<Integer, Long> closedMaxSeq = new HashMap<>();
    private final Set<Long> inFlight = new HashSet<>();
    private final TreeMap<Long, Pending> pending = new TreeMap<>();
    private long ackedThrough;
    private long nextSeq = 1;
    private int segmentIndex = 1;
    private FileChannel channel;
    private long segmentSize;
    private long usedBytes;
    private long unackedRecords;
    private long replayedBatches;
    private long errors;
    private boolean quotaHit;
    private String spoolId;

    public HistorianDiskSpool(Path directory, long maxBytes, long segmentBytes, boolean fsync, ObjectMapper objectMapper) {
        this.directory = directory;
        this.maxBytes = maxBytes;
        this.segmentBytes = Math.max(HEADER + 64, segmentBytes);
        this.fsync = fsync;
        this.objectMapper = objectMapper;
    }

    public synchronized void open() throws IOException {
        Files.createDirectories(directory);
        loadOrCreateSpoolId();
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
        reclaimClosedSegments();
        openSegment(segmentIndex, true);
    }

    public synchronized String append(List<VariableHistoryWriteRecord> records) throws IOException {
        return appendInternal(records, false);
    }

    /** Durable append that is invisible to replay until {@link #release(String)}. */
    public synchronized String appendClaimed(List<VariableHistoryWriteRecord> records) throws IOException {
        return appendInternal(records, true);
    }

    public String spoolId() {
        return spoolId;
    }

    private String appendInternal(List<VariableHistoryWriteRecord> records, boolean claim) throws IOException {
        if (records == null || records.isEmpty()) {
            return null;
        }
        byte[] payload = encode(records);
        if (!validLength(payload.length)) {
            throw new IOException(
                    "historian spool frame exceeds max size (" + payload.length + " > " + MAX_FRAME_BYTES + ")");
        }
        return write(records, payload, claim);
    }

    private String write(List<VariableHistoryWriteRecord> records, byte[] payload, boolean claim) throws IOException {
        int frame = HEADER + payload.length;
        ensureCapacity(frame);
        if (channel == null || !channel.isOpen() || segmentSize + frame > segmentBytes) {
            if (segmentSize + frame > segmentBytes && channel != null && channel.isOpen()) {
                rotate();
            } else if (channel == null || !channel.isOpen()) {
                reopenActiveSegment();
            }
            if (segmentSize + frame > segmentBytes) {
                rotate();
            }
        }
        long seq = nextSeq;
        long position = segmentSize;
        long createdAt = System.currentTimeMillis();
        ByteBuffer header = ByteBuffer.allocate(HEADER);
        header.putInt(MAGIC);
        header.putInt(payload.length);
        header.putInt(crc(payload));
        header.putLong(createdAt);
        header.flip();
        try {
            writeFully(header);
            writeFully(ByteBuffer.wrap(payload));
            if (fsync) {
                channel.force(true);
            }
        } catch (IOException ex) {
            boolean interrupted = Thread.interrupted();
            try {
                if (channel == null || !channel.isOpen()) {
                    reopenActiveSegment();
                }
                channel.truncate(position);
                segmentSize = position;
            } catch (IOException truncate) {
                ex.addSuppressed(truncate);
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            throw ex;
        }
        nextSeq = seq + 1;
        segmentSize += frame;
        usedBytes += frame;
        pending.put(seq, new Pending(records.size(), createdAt));
        unackedRecords += records.size();
        if (claim) {
            inFlight.add(seq);
        }
        return batchId(seq);
    }

    public synchronized void ack(String batchId) throws IOException {
        if (batchId == null) {
            return;
        }
        long seq = parseSeq(batchId);
        inFlight.remove(seq);
        Pending removed = pending.remove(seq);
        if (removed != null) {
            unackedRecords = Math.max(0, unackedRecords - removed.records());
        }
        if (seq <= ackedThrough) {
            return;
        }
        ackedAbove.add(seq);
        while (ackedAbove.contains(ackedThrough + 1)) {
            ackedAbove.remove(ackedThrough + 1);
            ackedThrough++;
        }
        writeCheckpoint();
        reclaimClosedSegments();
    }

    public synchronized void claim(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return;
        }
        inFlight.add(parseSeq(batchId));
    }

    public synchronized void release(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return;
        }
        inFlight.remove(parseSeq(batchId));
    }

    public int replay(Replay sink, int limit) throws IOException {
        List<Frame> pending;
        synchronized (this) {
            pending = readUnacked(limit);
            for (Frame frame : pending) {
                inFlight.add(frame.seq());
            }
        }
        int copied = 0;
        for (Frame frame : pending) {
            String id = batchId(frame.seq());
            try {
                sink.accept(id, frame.records());
                synchronized (this) {
                    ack(id);
                    replayedBatches++;
                }
                copied++;
            } catch (RuntimeException ex) {
                synchronized (this) {
                    for (int index = copied; index < pending.size(); index++) {
                        inFlight.remove(pending.get(index).seq());
                    }
                    errors++;
                }
                return copied;
            }
        }
        return copied;
    }

    public synchronized Stats stats() {
        long oldest = pending.isEmpty() ? 0 : pending.firstEntry().getValue().createdAtMillis();
        long age = oldest == 0 ? 0 : Math.max(0, System.currentTimeMillis() - oldest);
        long lag = Math.max(0, unackedRecords);
        return new Stats(usedBytes, lag, age, replayedBatches, errors, quotaHit);
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
            closedMaxSeq.put(segmentIndex, nextSeq - 1);
            segmentIndex++;
        }
        reclaimClosedSegments();
        openSegment(segmentIndex, false);
    }

    private void ensureCapacity(int frame) throws IOException {
        if (usedBytes + frame <= maxBytes) {
            quotaHit = false;
            return;
        }
        reclaimClosedSegments();
        if (usedBytes + frame <= maxBytes) {
            quotaHit = false;
            return;
        }
        if (segmentSize > 0 && nextSeq - 1 <= ackedThrough) {
            rotate();
        }
        if (usedBytes + frame > maxBytes) {
            quotaHit = true;
            throw new HistorianSpoolQuotaException(usedBytes, maxBytes);
        }
        quotaHit = false;
    }

    private void reclaimClosedSegments() throws IOException {
        var iterator = closedMaxSeq.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey() == segmentIndex || entry.getValue() > ackedThrough) {
                continue;
            }
            Path path = directory.resolve(String.format("%06d.seg", entry.getKey()));
            if (Files.exists(path)) {
                usedBytes -= Files.size(path);
                Files.delete(path);
            }
            iterator.remove();
        }
        if (usedBytes < 0) {
            usedBytes = 0;
        }
        if (usedBytes < maxBytes) {
            quotaHit = false;
        }
    }

    private List<Frame> readUnacked(int limit) throws IOException {
        if (limit <= 0 || pending.isEmpty() || allPendingInFlight()) {
            return List.of();
        }
        List<Frame> found = new ArrayList<>();
        long payloadBytes = 0;
        for (Path segment : listSegments()) {
            if (found.size() >= limit) {
                break;
            }
            Long closedMax = closedMaxSeq.get((int) segmentNumber(segment));
            if (closedMax != null && closedMax <= ackedThrough) {
                continue;
            }
            try (FileChannel reader = FileChannel.open(segment, StandardOpenOption.READ)) {
                ByteBuffer header = ByteBuffer.allocate(HEADER);
                while (found.size() < limit) {
                    header.clear();
                    if (!readFully(reader, header)) {
                        break;
                    }
                    header.flip();
                    int magic = header.getInt();
                    int length = header.getInt();
                    int expected = header.getInt();
                    header.getLong();
                    if (!validLength(length) || magic != MAGIC) {
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
                    long seq = sequenceOf(payload);
                    if (seq < 0 || seq <= ackedThrough || ackedAbove.contains(seq) || inFlight.contains(seq)) {
                        continue;
                    }
                    if (payloadBytes + length > REPLAY_PAYLOAD_BUDGET && !found.isEmpty()) {
                        break;
                    }
                    payloadBytes += length;
                    found.add(decode(payload));
                }
            }
            if (found.size() >= limit || (payloadBytes >= REPLAY_PAYLOAD_BUDGET && !found.isEmpty())) {
                break;
            }
        }
        return found;
    }

    private boolean allPendingInFlight() {
        for (Long seq : pending.keySet()) {
            if (!inFlight.contains(seq)) {
                return false;
            }
        }
        return true;
    }

    private void writeFully(ByteBuffer buffer) throws IOException {
        if (channel == null || !channel.isOpen()) {
            reopenActiveSegment();
        }
        while (buffer.hasRemaining()) {
            if (channel.write(buffer) < 0) {
                throw new IOException("historian spool write failed");
            }
        }
    }

    private void openSegment(int index, boolean append) throws IOException {
        Path path = directory.resolve(String.format("%06d.seg", index));
        if (append) {
            channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            segmentSize = channel.size();
            channel.position(segmentSize);
        } else {
            channel = FileChannel.open(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            segmentSize = 0;
        }
    }

    /** Reopens the active segment after {@link #truncateTornTail} or a failed partial write. */
    private void reopenActiveSegment() throws IOException {
        Path path = directory.resolve(String.format("%06d.seg", segmentIndex));
        channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        channel.truncate(segmentSize);
        channel.position(segmentSize);
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
                if (magic != MAGIC || !validLength(length)) {
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
            long segmentMax = 0;
            boolean sawFrame = false;
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
                    long createdAt = header.getLong();
                    if (!validLength(length)) {
                        break;
                    }
                    ByteBuffer body = ByteBuffer.allocate(length);
                    if (!readFully(reader, body) || crc(body.array()) != expected) {
                        break;
                    }
                    Frame frame = decode(body.array());
                    sawFrame = true;
                    segmentMax = Math.max(segmentMax, frame.seq());
                    max = Math.max(max, frame.seq());
                    if (frame.seq() > ackedThrough && !ackedAbove.contains(frame.seq())) {
                        pending.put(frame.seq(), new Pending(frame.records().size(), createdAt));
                        unackedRecords += frame.records().size();
                    }
                }
            }
            if (sawFrame && !segment.equals(segments.getLast())) {
                String name = segment.getFileName().toString();
                closedMaxSeq.put(Integer.parseInt(name.substring(0, name.indexOf('.'))), segmentMax);
            }
        }
        nextSeq = max + 1;
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
                    instant(node.path("sampledAt")),
                    instant(node.path("observedAt")),
                    node.path("valueDouble").isNull() ? null : node.path("valueDouble").asDouble(),
                    node.path("valueText").isNull() ? null : node.path("valueText").asText(null),
                    node.path("quality").asText(null)
            ));
        }
        return new Frame(seq, records);
    }

    private void loadOrCreateSpoolId() throws IOException {
        Path path = directory.resolve("spool.id");
        if (Files.exists(path)) {
            spoolId = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (spoolId.isBlank()) {
                spoolId = UUID.randomUUID().toString();
                writeSpoolId(path);
            }
            return;
        }
        spoolId = UUID.randomUUID().toString();
        writeSpoolId(path);
    }

    private void writeSpoolId(Path path) throws IOException {
        Path temporary = directory.resolve("spool.id.tmp");
        Files.writeString(temporary, spoolId + "\n", StandardCharsets.UTF_8);
        if (fsync) {
            try (FileChannel idChannel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                idChannel.force(true);
            }
        }
        try {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String batchId(long seq) {
        return spoolId + "-" + seq;
    }

    private static long parseSeq(String batchId) {
        int dash = batchId.lastIndexOf('-');
        if (dash >= 0 && dash < batchId.length() - 1) {
            return Long.parseLong(batchId.substring(dash + 1));
        }
        return Long.parseLong(batchId);
    }

    private void loadCheckpoint() throws IOException {
        Path path = directory.resolve("checkpoint");
        if (!Files.exists(path)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (java.nio.charset.CharacterCodingException ex) {
            ackedThrough = 0;
            ackedAbove.clear();
            return;
        } catch (java.io.UncheckedIOException ex) {
            if (!(ex.getCause() instanceof java.nio.charset.CharacterCodingException)) {
                throw ex;
            }
            ackedThrough = 0;
            ackedAbove.clear();
            return;
        }
        try {
            if (!lines.isEmpty() && !lines.getFirst().isBlank()) {
                ackedThrough = Math.max(0, Long.parseLong(lines.getFirst().trim()));
            }
            if (lines.size() > 1 && !lines.get(1).isBlank()) {
                for (String part : lines.get(1).split(",")) {
                    if (!part.isBlank()) {
                        long gap = Long.parseLong(part.trim());
                        if (gap > ackedThrough) {
                            ackedAbove.add(gap);
                        }
                    }
                }
            }
        } catch (NumberFormatException ex) {
            ackedThrough = 0;
            ackedAbove.clear();
        }
    }

    private void writeCheckpoint() throws IOException {
        String gaps = String.join(",", ackedAbove.stream().map(String::valueOf).toList());
        Path path = directory.resolve("checkpoint");
        Path temporary = directory.resolve("checkpoint.tmp");
        Files.writeString(temporary, ackedThrough + "\n" + gaps + "\n", StandardCharsets.UTF_8);
        if (fsync) {
            try (FileChannel checkpoint = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                checkpoint.force(true);
            }
        }
        try {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static boolean validLength(int length) {
        return length > 0 && length <= MAX_FRAME_BYTES;
    }

    private static java.time.Instant instant(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return java.time.Instant.ofEpochMilli(node.asLong());
    }

    private List<Path> listSegments() throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".seg"))
                    .sorted(Comparator.comparingLong(HistorianDiskSpool::segmentNumber))
                    .toList();
        }
    }

    private static long segmentNumber(Path path) {
        String name = path.getFileName().toString();
        return Long.parseLong(name.substring(0, name.indexOf('.')));
    }

    private static boolean readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                return false;
            }
        }
        return true;
    }

    private static long sequenceOf(byte[] payload) {
        int end = 0;
        while (end < payload.length && payload[end] != '\n') {
            end++;
        }
        if (end == 0 || end >= payload.length) {
            return -1;
        }
        try {
            return Long.parseLong(new String(payload, 0, end, StandardCharsets.UTF_8));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static int crc(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        return (int) crc.getValue();
    }

    public record Stats(long bytes, long unackedRecords, long oldestAgeMillis, long replayedBatches, long errors, boolean quotaHit) {
    }

    private record Pending(int records, long createdAtMillis) {
    }

    public record Frame(long seq, List<VariableHistoryWriteRecord> records) {
    }

    @FunctionalInterface
    public interface Replay {
        void accept(String batchId, List<VariableHistoryWriteRecord> records);
    }
}
