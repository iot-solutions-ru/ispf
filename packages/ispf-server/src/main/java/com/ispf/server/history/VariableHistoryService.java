package com.ispf.server.history;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.object.Variable;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import com.ispf.server.persistence.entity.VariableSampleEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
    private final VariableHistoryAsyncWriter asyncWriter;
    private final VariableHistoryBatchPersister batchPersister;
    private final VariableHistoryQueryStore queryStore;

    /** Last sample epoch ms per (path|var|field) for debounce. */
    private final ConcurrentHashMap<String, Long> lastSampleMs = new ConcurrentHashMap<>();

    public VariableHistoryService(
            VariableHistoryProperties properties,
            VariableSampleRepository sampleRepository,
            ObjectVariableRepository variableRepository,
            ObjectManager objectManager,
            ObjectMapper objectMapper,
            PlatformLeaderLockService leaderLockService,
            VariableHistoryAsyncWriter asyncWriter,
            VariableHistoryBatchPersister batchPersister,
            VariableHistoryQueryStore queryStore
    ) {
        this.properties = properties;
        this.sampleRepository = sampleRepository;
        this.variableRepository = variableRepository;
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
        this.leaderLockService = leaderLockService;
        this.asyncWriter = asyncWriter;
        this.batchPersister = batchPersister;
        this.queryStore = queryStore;
    }

    public void recordVariableUpdate(String objectPath, String variableName) {
        List<VariableSampleEntity> samples = collectSamples(objectPath, variableName);
        if (samples.isEmpty()) {
            return;
        }
        if (asyncWriter.isAsyncEnabled()) {
            asyncWriter.enqueue(samples);
        } else {
            batchPersister.persistBatch(samples);
        }
    }

    private List<VariableSampleEntity> collectSamples(String objectPath, String variableName) {
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
            sample.setValueDouble(numeric.get());
            samples.add(sample);
            lastSampleMs.put(debounceKey, nowMs);
        }
        return samples;
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

        List<VariableHistorySample> samples = queryStore.query(
                objectPath,
                variableName,
                field,
                from,
                to,
                cappedLimit
        );

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
        objectManager.require(objectPath).getVariable(variableName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + variableName));

        String field = fieldName == null || fieldName.isBlank() ? "value" : fieldName;
        Duration bucket = parseBucket(bucketSpec);
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
                    List.of()
            );
        }

        int cappedBuckets = Math.min(Math.max(maxBuckets, 1), 2_000);
        List<VariableHistoryBucket> result = queryStore.aggregateBuckets(
                objectPath,
                variableName,
                field,
                resolvedFrom,
                resolvedTo,
                bucket,
                cappedBuckets
        );

        return new VariableHistoryAggregateResponse(
                objectPath,
                variableName,
                field,
                formatBucket(bucket),
                result
        );
    }

    static Duration parseBucket(String bucketSpec) {
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

    private static String formatBucket(Duration bucket) {
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
        return objectManager.require(objectPath)
                .getVariable(variableName)
                .map(Variable::historyEnabled)
                .orElse(false);
    }

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

    public record VariableHistorySample(Instant ts, Double value, String text) {
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
            List<VariableHistoryBucket> buckets
    ) {
    }
}
