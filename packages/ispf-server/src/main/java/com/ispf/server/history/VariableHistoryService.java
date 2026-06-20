package com.ispf.server.history;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VariableHistoryService {

    private final VariableHistoryProperties properties;
    private final VariableSampleRepository sampleRepository;
    private final ObjectVariableRepository variableRepository;
    private final ObjectManager objectManager;

    /** Last sample epoch ms per (path|var|field) for debounce. */
    private final ConcurrentHashMap<String, Long> lastSampleMs = new ConcurrentHashMap<>();

    public VariableHistoryService(
            VariableHistoryProperties properties,
            VariableSampleRepository sampleRepository,
            ObjectVariableRepository variableRepository,
            ObjectManager objectManager
    ) {
        this.properties = properties;
        this.sampleRepository = sampleRepository;
        this.variableRepository = variableRepository;
        this.objectManager = objectManager;
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
}
