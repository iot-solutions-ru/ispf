package com.ispf.server.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.object.Variable;
import com.ispf.server.config.VariableHistoryProperties;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.ObjectVariableRepository;
import com.ispf.server.persistence.VariableSampleRepository;
import com.ispf.server.persistence.entity.ObjectVariableEntity;
import com.ispf.server.persistence.entity.VariableSampleEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final VariableHistoryProperties properties;
    private final VariableSampleRepository sampleRepository;
    private final ObjectVariableRepository variableRepository;
    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    /** Last sample epoch ms per (path|var|field) for debounce. */
    private final ConcurrentHashMap<String, Long> lastSampleMs = new ConcurrentHashMap<>();

    public VariableHistoryService(
            VariableHistoryProperties properties,
            VariableSampleRepository sampleRepository,
            ObjectVariableRepository variableRepository,
            ObjectManager objectManager,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.sampleRepository = sampleRepository;
        this.variableRepository = variableRepository;
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordVariableUpdate(String objectPath, String variableName) {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.getExcludedVariables().contains(variableName)
                || variableName.startsWith("driver")) {
            return;
        }
        if (!isHistorizedVariable(objectPath, variableName)) {
            return;
        }

        Variable variable = objectManager.require(objectPath).getVariable(variableName).orElse(null);
        if (variable == null || variable.value().isEmpty()) {
            return;
        }

        DataRecord record = variable.value().get();
        if (record.rowCount() == 0) {
            return;
        }

        Map<String, Object> row = record.firstRow();
        Instant now = Instant.now();
        long nowMs = now.toEpochMilli();

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
            sampleRepository.save(sample);
            lastSampleMs.put(debounceKey, nowMs);
        }
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

        List<VariableSampleEntity> rows;
        if (from != null && to != null) {
            rows = sampleRepository.findByObjectPathAndVariableNameAndFieldNameAndSampledAtBetweenOrderBySampledAtAsc(
                    objectPath,
                    variableName,
                    field,
                    from,
                    to
            );
            if (rows.size() > cappedLimit) {
                rows = rows.subList(rows.size() - cappedLimit, rows.size());
            }
        } else {
            rows = new ArrayList<>(sampleRepository.findByObjectPathAndVariableNameAndFieldNameOrderBySampledAtDesc(
                    objectPath,
                    variableName,
                    field,
                    PageRequest.of(0, cappedLimit)
            ));
            rows = rows.reversed();
        }

        List<VariableHistorySample> samples = rows.stream()
                .map(row -> new VariableHistorySample(
                        row.getSampledAt(),
                        row.getValueDouble(),
                        row.getValueText()
                ))
                .toList();

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
        List<VariableSampleEntity> rows = sampleRepository
                .findByObjectPathAndVariableNameAndFieldNameAndSampledAtBetweenOrderBySampledAtAsc(
                        objectPath,
                        variableName,
                        field,
                        resolvedFrom,
                        resolvedTo
                );

        Map<Instant, BucketAccumulator> buckets = new LinkedHashMap<>();
        for (VariableSampleEntity row : rows) {
            Double value = row.getValueDouble();
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            Instant bucketStart = truncateToBucket(row.getSampledAt(), bucket);
            buckets.computeIfAbsent(bucketStart, ignored -> new BucketAccumulator()).add(value);
        }

        List<VariableHistoryBucket> result = buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().toBucket(entry.getKey()))
                .toList();

        if (result.size() > cappedBuckets) {
            result = result.subList(result.size() - cappedBuckets, result.size());
        }

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

    private static Instant truncateToBucket(Instant instant, Duration bucket) {
        long bucketSeconds = bucket.getSeconds();
        if (bucketSeconds <= 0) {
            return instant;
        }
        long floored = Math.floorDiv(instant.getEpochSecond(), bucketSeconds) * bucketSeconds;
        return Instant.ofEpochSecond(floored);
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

    private static final class BucketAccumulator {
        private double sum;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;
        private int count;

        void add(double value) {
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
            count++;
        }

        VariableHistoryBucket toBucket(Instant ts) {
            return new VariableHistoryBucket(ts, sum / count, min, max, count);
        }
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
