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
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.CRC32;

/**
 * Segmented store-and-forward log for sole-store ClickHouse.
 * A frame is durable before ClickHouse is called. Quota does not delete old samples.
 */
public final class HistorianDiskSpool {

    static final int MAGIC = 0x48535031;
    private static final int HEADER = 20;
    private static final int MAX_FRAME_BYTES = 32 * 1024 * 1024;

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
    private final long[] acceptMillis = new long[1024];
    private int acceptCount;
    private int acceptCursor;
    private boolean forceLeader;
    private long forcedThrough;
    private long writtenThrough;
    private ExecutorService replayPool;

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
        reclaimClosedSegments();
        openSegment(segmentIndex, true);
    }

    public String append(List<VariableHistoryWriteRecord> records) throws IOException {
        return write(records, false);
    }

    /** Durable append that is invisible to replay until {@link #release(String)}. */
    public String appendClaimed(List<VariableHistoryWriteRecord> records) throws IOException {
        return write(records, true);
    }

    private String write(List<VariableHistoryWriteRecord> records, boolean claim) throws IOException {
        if (records == null || records.isEmpty()) {
            return null;
        }
        long seq;
        synchronized (this) {
            seq = nextSeq++;
        }
        byte[] payload = encode(seq, records);
        synchronized (this) {
            try {
                place(payload, seq, records, claim);
            } catch (IOException | RuntimeException ex) {
                if (nextSeq == seq + 1) {
                    nextSeq = seq;
                }
                throw ex;
            }
        }
        if (fsync) {
            forceThrough(seq);
        }
        return Long.toString(seq);
    }

    private void place(byte[] payload, long seq, List<VariableHistoryWriteRecord> records, boolean claim) throws IOException {
        int frame = HEADER + payload.length;
        ensureCapacity(frame);
        if (channel == null || (segmentSize + frame > segmentBytes && !forceLeader)) {
            rotate();
        }
        long position = channel.position();
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
        } catch (IOException ex) {
            try {
                channel.truncate(position);
            } catch (IOException truncate) {
                ex.addSuppressed(truncate);
            }
            throw ex;
        }
        segmentSize += frame;
        usedBytes += frame;
        pending.put(seq, new Pending(records.size(), createdAt));
        unackedRecords += records.size();
        writtenThrough = seq;
        if (claim) {
            inFlight.add(seq);
        }
    }

    private void forceThrough(long seq) throws IOException {
        for (;;) {
            FileChannel current;
            long covered;
            boolean sameChannel;
            synchronized (this) {
                if (forcedThrough >= seq) {
                    return;
                }
                if (forceLeader) {
                    try {
                        wait(1_000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IOException("historian spool fsync interrupted", ex);
                    }
                    continue;
                }
                forceLeader = true;
                current = channel;
                covered = writtenThrough;
            }
            IOException failure = null;
            try {
                if (current != null) {
                    current.force(true);
                }
            } catch (IOException ex) {
                failure = ex;
            }
            synchronized (this) {
                sameChannel = Objects.equals(current, channel);
                if (failure == null) {
                    forcedThrough = Math.max(forcedThrough, covered);
                }
                forceLeader = false;
                notifyAll();
                if (failure != null && sameChannel) {
                    throw failure;
                }
            }
        }
    }

    public synchronized void ack(String batchId) throws IOException {
        if (batchId == null) {
            return;
        }
        long seq = Long.parseLong(batchId);
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
        inFlight.add(Long.parseLong(batchId));
    }

    public synchronized void release(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return;
        }
        inFlight.remove(Long.parseLong(batchId));
    }

    public int replay(Replay sink, int limit) throws IOException {
        List<Frame> pending;
        synchronized (this) {
            pending = readUnacked(limit);
            for (Frame frame : pending) {
                inFlight.add(frame.seq());
            }
        }
        if (pending.size() <= 1) {
            return replayOneByOne(sink, pending);
        }
        Set<Long> done = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();
        for (Frame frame : pending) {
            futures.add(replayPool().submit(() -> {
                sink.accept(Long.toString(frame.seq()), frame.records());
                try {
                    synchronized (HistorianDiskSpool.this) {
                        ack(Long.toString(frame.seq()));
                        replayedBatches++;
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException("historian spool ack failed", ex);
                }
                done.add(frame.seq());
                return null;
            }));
        }
        boolean failed = false;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failed = true;
            } catch (ExecutionException ex) {
                failed = true;
            }
        }
        synchronized (this) {
            for (Frame frame : pending) {
                if (!done.contains(frame.seq())) {
                    inFlight.remove(frame.seq());
                }
            }
            if (failed) {
                errors++;
            }
        }
        return done.size();
    }

    private int replayOneByOne(Replay sink, List<Frame> pending) throws IOException {
        int copied = 0;
        for (Frame frame : pending) {
            String id = Long.toString(frame.seq());
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

    private ExecutorService replayPool() {
        synchronized (this) {
            if (replayPool == null) {
                replayPool = Executors.newFixedThreadPool(8, runnable -> {
                    Thread thread = new Thread(runnable, "historian-spool-replay");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return replayPool;
        }
    }

    public synchronized Stats stats() {
        long oldest = pending.isEmpty() ? 0 : pending.firstEntry().getValue().createdAtMillis();
        long age = oldest == 0 ? 0 : Math.max(0, System.currentTimeMillis() - oldest);
        long lag = Math.max(0, unackedRecords);
        return new Stats(usedBytes, lag, age, replayedBatches, errors, quotaHit, acceptP99());
    }

    public synchronized void recordAccept(long millis) {
        acceptMillis[acceptCursor] = Math.max(0, millis);
        acceptCursor = (acceptCursor + 1) % acceptMillis.length;
        if (acceptCount < acceptMillis.length) {
            acceptCount++;
        }
    }

    private long acceptP99() {
        if (acceptCount == 0) {
            return 0;
        }
        long[] sample = Arrays.copyOf(acceptMillis, acceptCount);
        Arrays.sort(sample);
        int index = (int) Math.min(acceptCount - 1, Math.ceil(acceptCount * 0.99) - 1);
        return sample[Math.max(0, index)];
    }

    public synchronized void close() throws IOException {
        if (replayPool != null) {
            replayPool.shutdownNow();
            replayPool = null;
        }
        if (channel != null) {
            if (fsync) {
                channel.force(true);
                forcedThrough = Math.max(forcedThrough, nextSeq - 1);
            }
            channel.close();
            channel = null;
        }
    }

    private void rotate() throws IOException {
        if (channel != null) {
            if (fsync) {
                channel.force(true);
                forcedThrough = Math.max(forcedThrough, writtenThrough);
            }
            channel.close();
            channel = null;
        }
        if (segmentSize > 0) {
            closedMaxSeq.put(segmentIndex, writtenThrough);
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
        if (segmentSize > 0 && writtenThrough > 0 && writtenThrough <= ackedThrough) {
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
                    found.add(decode(payload));
                }
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
        while (buffer.hasRemaining()) {
            if (channel.write(buffer) < 0) {
                throw new IOException("historian spool write failed");
            }
        }
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
        writtenThrough = max;
    }

    private byte[] encode(long seq, List<VariableHistoryWriteRecord> records) throws IOException {
        StringBuilder body = new StringBuilder();
        body.append(seq).append('\n');
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

    private static boolean validLength(int length) {
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

    public record Stats(
            long bytes,
            long unackedRecords,
            long oldestAgeMillis,
            long replayedBatches,
            long errors,
            boolean quotaHit,
            long acceptP99Millis
    ) {
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
