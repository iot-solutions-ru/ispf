package com.ispf.server.history;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.object.Variable;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import com.ispf.server.persistence.entity.VariableSampleEntity;
import com.ispf.server.object.CoalescedTelemetryUpdate;
import com.ispf.server.platform.analytics.engine.AnalyticsOnChangeTrigger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VariableHistoryService {

    private static final String RETENTION_LOCK = "variable_history_retention";

    private final VariableHistoryProperties properties;
    private final VariableSampleRepository sampleRepository;
    private final ObjectVariableRepository variableRepository;
    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;
    private final VariableHistoryAsyncWriter asyncWriter;
    private final VariableHistoryBatchPersister batchPersister;
    private final VariableHistoryQueryStore queryStore;
    private final HistorianQueryMetricsRecorder queryMetricsRecorder;
    private final ObjectProvider<AnalyticsOnChangeTrigger> analyticsOnChangeTrigger;
    private final ObjectProvider<HistorianRollupQueryService> rollupQueryService;

    /** Last sample epoch ms per (path|var|field) for debounce. */
    private final ConcurrentHashMap<String, Long> lastSampleMs = new ConcurrentHashMap<>();
    /** Short-lived cache for historyEnabled lookups on the hot path. */
    private final ConcurrentHashMap<String, HistorizedCacheEntry> historizedCache = new ConcurrentHashMap<>();
    private static final long HISTORIZED_CACHE_TTL_MS = 10_000;

    public VariableHistoryService(
            VariableHistoryProperties properties,
            VariableSampleRepository sampleRepository,
            ObjectVariableRepository variableRepository,
            ObjectManager objectManager,
            ObjectMapper objectMapper,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties,
            VariableHistoryAsyncWriter asyncWriter,
            VariableHistoryBatchPersister batchPersister,
            VariableHistoryQueryStore queryStore,
            HistorianQueryMetricsRecorder queryMetricsRecorder,
            ObjectProvider<AnalyticsOnChangeTrigger> analyticsOnChangeTrigger,
            ObjectProvider<HistorianRollupQueryService> rollupQueryService
    ) {
        this.properties = properties;
        this.sampleRepository = sampleRepository;
        this.variableRepository = variableRepository;
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
        this.asyncWriter = asyncWriter;
        this.batchPersister = batchPersister;
        this.queryStore = queryStore;
        this.queryMetricsRecorder = queryMetricsRecorder;
        this.analyticsOnChangeTrigger = analyticsOnChangeTrigger;
        this.rollupQueryService = rollupQueryService;
    }

    public void recordVariableUpdate(String objectPath, String variableName) {
        recordVariableUpdate(objectPath, variableName, null);
    }

    public void recordVariableUpdate(String objectPath, String variableName, Instant observedAt) {
        List<VariableSampleEntity> samples = collectSamples(objectPath, variableName, observedAt);
        if (samples.isEmpty()) {
            return;
        }
        if (asyncWriter.isAsyncEnabled()) {
            asyncWriter.enqueue(samples);
        } else {
            batchPersister.persistBatch(samples);
        }
    }

    private List<VariableSampleEntity> collectSamples(String objectPath, String variableName, Instant observedAt) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        if (properties.getExcludedVariables().contains(variableName)
                || variableName.startsWith("driver")) {
            return List.of();
        }
        if (!isHistorizedVariable(objectPath, variableName)) {
            return List.of();
        }

        Variable variable = objectManager.require(objectPath).getVariable(variableName).orElse(null);
        if (variable == null || variable.value().isEmpty()) {
            return List.of();
        }

        DataRecord record = variable.value().get();
        if (record.rowCount() == 0) {
            return List.of();
        }

        Map<String, Object> row = record.firstRow();
        Instant now = Instant.now();
        long nowMs = now.toEpochMilli();
        List<VariableSampleEntity> samples = new ArrayList<>();

        for (FieldDefinition field : record.schema().fields()) {
            String fieldName = field.name();
            Object raw = row.get(fieldName);
            Optional<Double> numeric = toNumeric(raw);
            if (numeric.isEmpty()) {
                continue;
            }

            String debounceKey = objectPath + "|" + variableName + "|" + fieldName;
            Long lastMs = lastSampleMs.get(debounceKey);
            if (lastMs != null && nowMs - lastMs < properties.getMinIntervalMs()) {
                continue;
            }

            VariableSampleEntity sample = new VariableSampleEntity();
            sample.setObjectPath(objectPath);
            sample.setVariableName(variableName);
            sample.setFieldName(fieldName);
            sample.setSampledAt(now);
            sample.setObservedAt(observedAt != null ? observedAt : now);
            sample.setValueDouble(numeric.get());
            samples.add(sample);
            lastSampleMs.put(debounceKey, nowMs);
        }
        return samples;
    }

    /**
     * Records a historian sample with an explicit device measurement time (ADR-0020 observedAt).
     */
    public void recordObservedSample(
            String objectPath,
            String variableName,
            String fieldName,
            double value,
            Instant observedAt
    ) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!isHistorizedVariable(objectPath, variableName)) {
            return;
        }
        Instant ingestedAt = Instant.now();
        Instant effectiveObserved = observedAt != null ? observedAt : ingestedAt;
        VariableSampleEntity sample = new VariableSampleEntity();
        sample.setObjectPath(objectPath);
        sample.setVariableName(variableName);
        sample.setFieldName(fieldName == null || fieldName.isBlank() ? "value" : fieldName);
        sample.setSampledAt(ingestedAt);
        sample.setObservedAt(effectiveObserved);
        sample.setValueDouble(value);
        if (asyncWriter.isAsyncEnabled()) {
            asyncWriter.enqueue(List.of(sample));
        } else {
            batchPersister.persistOne(sample);
        }
    }

    /**
     * High-throughput path: samples numeric fields from the supplied {@link DataRecord} without re-reading
     * the in-memory variable (used by {@link TelemetryHistorianFastPath}).
     */
    public void recordFromDataRecord(
            String objectPath,
            String variableName,
            DataRecord record,
            Instant observedAt
    ) {
        if (!properties.isEnabled() || record == null || record.rowCount() == 0) {
            return;
        }
        if (properties.getExcludedVariables().contains(variableName)
                || variableName.startsWith("driver")) {
            return;
        }
        if (!isHistorizedVariable(objectPath, variableName)) {
            return;
        }
        enqueueFromDataRecord(objectPath, variableName, record, observedAt);
    }

    /**
     * Fast path when {@link TelemetryHistorianFastPath} already verified historian interest.
     */
    public void recordFromDataRecordTrusted(
            String objectPath,
            String variableName,
            DataRecord record,
            Instant observedAt
    ) {
        if (!properties.isEnabled() || record == null || record.rowCount() == 0) {
            return;
        }
        if (properties.getExcludedVariables().contains(variableName)
                || variableName.startsWith("driver")) {
            return;
        }
        enqueueFromDataRecord(objectPath, variableName, record, observedAt);
    }

    /**
     * Batch trusted fast-path enqueue (single {@link VariableHistoryAsyncWriter#enqueue} per ingress drain batch).
     */
    public void recordFromDataRecordsTrusted(List<CoalescedTelemetryUpdate> updates) {
        if (!properties.isEnabled() || updates.isEmpty()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        Instant now = Instant.ofEpochMilli(nowMs);
        Map<String, VariableSampleEntity> coalesced = new LinkedHashMap<>();
        for (CoalescedTelemetryUpdate update : updates) {
            if (update.value() == null || update.value().rowCount() == 0) {
                continue;
            }
            if (properties.getExcludedVariables().contains(update.variableName())
                    || update.variableName().startsWith("driver")) {
                continue;
            }
            appendSamplesFromDataRecord(
                    update.path(),
                    update.variableName(),
                    update.value(),
                    update.observedAt(),
                    now,
                    nowMs,
                    coalesced
            );
        }
        List<VariableSampleEntity> samples = new ArrayList<>(coalesced.size());
        for (Map.Entry<String, VariableSampleEntity> entry : coalesced.entrySet()) {
            if (!acceptDebouncedSample(entry.getKey(), entry.getValue(), nowMs)) {
                continue;
            }
            samples.add(entry.getValue());
        }
        enqueueSamples(samples);
    }

    private boolean acceptDebouncedSample(String debounceKey, VariableSampleEntity sample, long fallbackMs) {
        long debounceMs = sample.getObservedAt() != null
                ? sample.getObservedAt().toEpochMilli()
                : fallbackMs;
        Long lastMs = lastSampleMs.get(debounceKey);
        if (lastMs != null && debounceMs - lastMs < properties.getMinIntervalMs()) {
            return false;
        }
        lastSampleMs.put(debounceKey, debounceMs);
        return true;
    }

    private void enqueueFromDataRecord(
            String objectPath,
            String variableName,
            DataRecord record,
            Instant observedAt
    ) {
        long nowMs = System.currentTimeMillis();
        Instant now = Instant.ofEpochMilli(nowMs);
        List<VariableSampleEntity> samples = new ArrayList<>();
        appendSamplesFromDataRecord(objectPath, variableName, record, observedAt, now, nowMs, samples);
        enqueueSamples(samples);
    }

    private void appendSamplesFromDataRecord(
            String objectPath,
            String variableName,
            DataRecord record,
            Instant observedAt,
            Instant now,
            long nowMs,
            Map<String, VariableSampleEntity> samples
    ) {
        Instant effectiveObserved = observedAt != null ? observedAt : now;

        for (var field : record.schema().fields()) {
            String fieldName = field.name();
            Object raw = record.firstRow().get(fieldName);
            Optional<Double> numeric = toNumeric(raw);
            if (numeric.isEmpty()) {
                continue;
            }
            String debounceKey = objectPath + "|" + variableName + "|" + fieldName;
            VariableSampleEntity sample = new VariableSampleEntity();
            sample.setObjectPath(objectPath);
            sample.setVariableName(variableName);
            sample.setFieldName(fieldName);
            sample.setSampledAt(now);
            sample.setObservedAt(effectiveObserved);
            sample.setValueDouble(numeric.get());
            samples.put(debounceKey, sample);
        }
    }

    private void appendSamplesFromDataRecord(
            String objectPath,
            String variableName,
            DataRecord record,
            Instant observedAt,
            Instant now,
            long nowMs,
            List<VariableSampleEntity> samples
    ) {
        Map<String, VariableSampleEntity> coalesced = new LinkedHashMap<>();
        appendSamplesFromDataRecord(objectPath, variableName, record, observedAt, now, nowMs, coalesced);
        for (Map.Entry<String, VariableSampleEntity> entry : coalesced.entrySet()) {
            if (acceptDebouncedSample(entry.getKey(), entry.getValue(), nowMs)) {
                samples.add(entry.getValue());
            }
        }
    }

    private void enqueueSamples(List<VariableSampleEntity> samples) {
        if (samples.isEmpty()) {
            return;
        }
        if (asyncWriter.isAsyncEnabled()) {
            asyncWriter.enqueue(samples);
        } else {
            batchPersister.persistBatch(samples);
        }
        analyticsOnChangeTrigger.ifAvailable(trigger -> {
            for (VariableSampleEntity sample : samples) {
                trigger.onSourceSample(sample.getObjectPath(), sample.getVariableName());
            }
        });
    }

    @Transactional(readOnly = true)
    public VariableHistoryResponse query(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    ) {
        objectManager.require(objectPath).getVariable(variableName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + variableName));

        String field = fieldName == null || fieldName.isBlank() ? "value" : fieldName;

        if (!isHistorizedVariable(objectPath, variableName)) {
            return new VariableHistoryResponse(objectPath, variableName, field, List.of());
        }

        int cappedLimit = Math.min(Math.max(limit, 1), 10_000);

        long started = System.nanoTime();
        List<VariableHistorySample> samples;
        try {
            samples = queryStore.query(
                    objectPath,
                    variableName,
                    field,
                    from,
                    to,
                    cappedLimit
            );
        } finally {
            queryMetricsRecorder.recordRawQuery(
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            );
        }

        return new VariableHistoryResponse(objectPath, variableName, field, samples);
    }

    @Transactional(readOnly = true)
    public VariableHistoryAggregateResponse aggregate(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            String bucketSpec,
            int maxBuckets
    ) {
        objectManager.require(objectPath);
        String field = fieldName == null || fieldName.isBlank() ? "value" : fieldName;
        Duration bucket = parseBucket(bucketSpec);
        if (objectManager.require(objectPath).getVariable(variableName).isEmpty()) {
            return new VariableHistoryAggregateResponse(
                    objectPath,
                    variableName,
                    field,
                    formatBucket(bucket),
                    List.of(),
                    "none"
            );
        }
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolveHistoryStart(objectPath, variableName, resolvedTo);
        if (resolvedFrom.isAfter(resolvedTo)) {
            resolvedFrom = resolvedTo.minus(1, ChronoUnit.HOURS);
        }

        if (!isHistorizedVariable(objectPath, variableName)) {
            return new VariableHistoryAggregateResponse(
                    objectPath,
                    variableName,
                    field,
                    formatBucket(bucket),
                    List.of(),
                    "none"
            );
        }

        int cappedBuckets = Math.min(Math.max(maxBuckets, 1), 2_000);
        long started = System.nanoTime();
        List<VariableHistoryBucket> result;
        String dataSource = "raw";
        try {
            HistorianRollupQueryService rollupSvc = rollupQueryService.getIfAvailable();
            Optional<HistorianRollupQueryService.RollupQueryResult> rollup = rollupSvc != null
                    ? rollupSvc.tryRollupQuery(
                            objectPath,
                            variableName,
                            field,
                            resolvedFrom,
                            resolvedTo,
                            bucket,
                            cappedBuckets
                    )
                    : Optional.empty();
            if (rollup.isPresent()) {
                result = rollup.orElseThrow().buckets();
                dataSource = rollup.orElseThrow().dataSource();
            } else {
                result = queryStore.aggregateBuckets(
                        objectPath,
                        variableName,
                        field,
                        resolvedFrom,
                        resolvedTo,
                        bucket,
                        cappedBuckets
                );
            }
        } finally {
            queryMetricsRecorder.recordAggregateQuery(
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            );
        }

        return new VariableHistoryAggregateResponse(
                objectPath,
                variableName,
                field,
                formatBucket(bucket),
                result,
                dataSource
        );
    }

    public static Duration parseBucket(String bucketSpec) {
        if (bucketSpec == null || bucketSpec.isBlank()) {
            throw new IllegalArgumentException("Bucket is required");
        }
        String normalized = bucketSpec.trim().toLowerCase(Locale.ROOT);
        Duration duration = switch (normalized) {
            case "1m" -> Duration.ofMinutes(1);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h" -> Duration.ofHours(1);
            case "6h" -> Duration.ofHours(6);
            case "8h" -> Duration.ofHours(8);
            case "1d" -> Duration.ofDays(1);
            default -> throw new IllegalArgumentException("Unsupported bucket: " + bucketSpec);
        };
        if (duration.compareTo(Duration.ofMinutes(1)) < 0 || duration.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Unsupported bucket: " + bucketSpec);
        }
        return duration;
    }

    private Instant resolveHistoryStart(String objectPath, String variableName, Instant to) {
        int retentionDays = variableRepository.findByObjectPathAndName(objectPath, variableName)
                .map(this::resolveRetentionDays)
                .orElse(properties.getRetentionDays());
        return to.minus(Math.max(retentionDays, 1), ChronoUnit.DAYS);
    }

    public static String formatBucket(Duration bucket) {
        long seconds = bucket.getSeconds();
        if (seconds % 86_400 == 0) {
            return (seconds / 86_400) + "d";
        }
        if (seconds % 3_600 == 0) {
            return (seconds / 3_600) + "h";
        }
        return (seconds / 60) + "m";
    }

    @Transactional(readOnly = true)
    public String exportCsv(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    ) {
        VariableHistoryResponse response = query(objectPath, variableName, fieldName, from, to, limit);
        StringBuilder csv = new StringBuilder("timestamp,field,value\n");
        for (VariableHistorySample sample : response.samples()) {
            csv.append(sample.ts()).append(',');
            csv.append(response.field()).append(',');
            csv.append(sample.value() != null ? sample.value() : "").append('\n');
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public void streamCsv(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit,
            OutputStream outputStream
    ) throws IOException {
        VariableHistoryResponse response = query(objectPath, variableName, fieldName, from, to, limit);
        outputStream.write("timestamp,field,value\n".getBytes(StandardCharsets.UTF_8));
        for (VariableHistorySample sample : response.samples()) {
            String line = sample.ts() + "," + response.field() + ","
                    + (sample.value() != null ? sample.value() : "") + "\n";
            outputStream.write(line.getBytes(StandardCharsets.UTF_8));
        }
        outputStream.flush();
    }

    @Transactional(readOnly = true)
    public byte[] exportJson(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    ) {
        VariableHistoryResponse response = query(objectPath, variableName, fieldName, from, to, limit);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(response);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize history export", ex);
        }
    }

    /**
     * Interim bulk export format for Parquet cold-tier (BL-163): one JSON object per sample line.
     */
    @Transactional(readOnly = true)
    public void streamJsonLines(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit,
            OutputStream outputStream
    ) throws IOException {
        VariableHistoryResponse response = query(objectPath, variableName, fieldName, from, to, limit);
        for (VariableHistorySample sample : response.samples()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("objectPath", response.objectPath());
            row.put("variableName", response.variableName());
            row.put("field", response.field());
            row.put("timestamp", sample.ts() != null ? sample.ts().toString() : null);
            row.put("value", sample.value());
            row.put("text", sample.text());
            if (sample.ingestedAt() != null) {
                row.put("ingestedAt", sample.ingestedAt().toString());
            }
            outputStream.write(objectMapper.writeValueAsBytes(row));
            outputStream.write('\n');
        }
        outputStream.flush();
    }

    @Transactional(readOnly = true)
    public byte[] exportParquet(
            String objectPath,
            String variableName,
            String fieldName,
            Instant from,
            Instant to,
            int limit
    ) throws IOException {
        VariableHistoryResponse response = query(objectPath, variableName, fieldName, from, to, limit);
        return VariableHistoryParquetExporter.export(response);
    }

    public static String exportInterimBulkFormat() {
        return "jsonl";
    }

    public static String exportFileName(String variableName, String field, String format) {
        String safeName = variableName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String safeField = field.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safeName + "-" + safeField + "." + format;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredSamples() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(RETENTION_LOCK, Duration.ofHours(1))) {
            return;
        }
        try {
            purgeExpiredSamplesInternal();
        } finally {
            leaderLockService.release(RETENTION_LOCK);
        }
    }

    void purgeExpiredSamplesInternal() {
        if (!queryStore.supportsApplicationRetentionPurge()) {
            return;
        }
        Instant now = Instant.now();
        for (ObjectVariableEntity entity : variableRepository.findByHistoryEnabledTrue()) {
            int retentionDays = resolveRetentionDays(entity);
            if (retentionDays <= 0) {
                continue;
            }
            Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
            sampleRepository.deleteByObjectPathAndVariableNameAndSampledAtBefore(
                    entity.getObjectPath(),
                    entity.getName(),
                    cutoff
            );
        }
    }

    private boolean isHistorizedVariable(String objectPath, String variableName) {
        String cacheKey = objectPath + "|" + variableName;
        long nowMs = System.currentTimeMillis();
        HistorizedCacheEntry cached = historizedCache.get(cacheKey);
        if (cached != null && nowMs - cached.loadedAtMs() < HISTORIZED_CACHE_TTL_MS) {
            return cached.enabled();
        }
        boolean enabled = objectManager.require(objectPath)
                .getVariable(variableName)
                .map(Variable::historyEnabled)
                .orElse(false);
        historizedCache.put(cacheKey, new HistorizedCacheEntry(enabled, nowMs));
        return enabled;
    }

    private record HistorizedCacheEntry(boolean enabled, long loadedAtMs) {}

    private int resolveRetentionDays(ObjectVariableEntity entity) {
        if (entity.getHistoryRetentionDays() != null) {
            return entity.getHistoryRetentionDays();
        }
        return properties.getRetentionDays();
    }

    private static Optional<Double> toNumeric(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof Boolean bool) {
            return Optional.of(bool ? 1.0 : 0.0);
        }
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                double value = Double.parseDouble(text.trim());
                return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public record VariableHistorySample(Instant ts, Double value, String text, Instant ingestedAt) {
        public VariableHistorySample(Instant ts, Double value, String text) {
            this(ts, value, text, null);
        }
    }

    public record VariableHistoryResponse(
            String objectPath,
            String variableName,
            String field,
            List<VariableHistorySample> samples
    ) {
    }

    public record VariableHistoryBucket(Instant ts, Double avg, Double min, Double max, int count) {
    }

    public record VariableHistoryAggregateResponse(
            String objectPath,
            String variableName,
            String field,
            String bucket,
            List<VariableHistoryBucket> buckets,
            String dataSource
    ) {
    }
}
