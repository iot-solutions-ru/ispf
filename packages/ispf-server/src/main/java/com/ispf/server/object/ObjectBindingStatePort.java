package com.ispf.server.object;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.BindingStatePort;
import com.ispf.expression.InMemoryBindingStatePort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleBinaryOperator;

/**
 * Persists stateful binding data in the {@link BindingStateVariables#BINDING_STATE} system variable per object.
 */
@Component
public class ObjectBindingStatePort implements BindingStatePort {

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final int MAX_TIMED_SAMPLES = InMemoryBindingStatePort.MAX_TIMED_SAMPLES;

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ObjectNode> cacheByObjectPath = new ConcurrentHashMap<>();

    public ObjectBindingStatePort(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Double> previousDouble(String key) {
        ObjectNode entry = entryForKey(key);
        if (entry == null || !entry.has("double")) {
            return Optional.empty();
        }
        return Optional.of(entry.get("double").asDouble());
    }

    @Override
    public Double putDouble(String key, double value) {
        ObjectNode entry = mutableEntryForKey(key);
        Double previous = entry.has("double") ? entry.get("double").asDouble() : null;
        entry.put("double", value);
        persist(key);
        return previous;
    }

    @Override
    public Optional<Long> previousTimestampMs(String key) {
        ObjectNode entry = entryForKey(key);
        if (entry == null || !entry.has("tsMs")) {
            return Optional.empty();
        }
        return Optional.of(entry.get("tsMs").asLong());
    }

    @Override
    public void putTimestampMs(String key, long timestampMs) {
        mutableEntryForKey(key).put("tsMs", timestampMs);
        persist(key);
    }

    @Override
    public Optional<Boolean> previousBoolean(String key) {
        ObjectNode entry = entryForKey(key);
        if (entry == null || !entry.has("bool")) {
            return Optional.empty();
        }
        return Optional.of(entry.get("bool").asBoolean());
    }

    @Override
    public void putBoolean(String key, boolean value) {
        mutableEntryForKey(key).put("bool", value);
        persist(key);
    }

    @Override
    public Optional<Double> aggregateTimedWindow(
            String key,
            long timestampMs,
            double value,
            long windowMs,
            DoubleBinaryOperator aggregator
    ) {
        ObjectNode entry = mutableEntryForKey(key);
        ArrayNode samples = samplesArray(entry);
        appendSample(samples, timestampMs, value, windowMs);
        if (samples.isEmpty()) {
            persist(key);
            return Optional.empty();
        }
        double result = samples.get(0).get("v").asDouble();
        for (int i = 1; i < samples.size(); i++) {
            result = aggregator.applyAsDouble(result, samples.get(i).get("v").asDouble());
        }
        persist(key);
        return Optional.of(result);
    }

    @Override
    public Optional<Double> averageTimedWindow(String key, long timestampMs, double value, long windowMs) {
        ObjectNode entry = mutableEntryForKey(key);
        ArrayNode samples = samplesArray(entry);
        appendSample(samples, timestampMs, value, windowMs);
        if (samples.isEmpty()) {
            persist(key);
            return Optional.empty();
        }
        double sum = 0;
        for (JsonNode sample : samples) {
            sum += sample.get("v").asDouble();
        }
        persist(key);
        return Optional.of(sum / samples.size());
    }

    @Override
    public void clearForTests() {
        cacheByObjectPath.clear();
    }

    public void invalidateCache(String objectPath) {
        cacheByObjectPath.remove(objectPath);
    }

    private ObjectNode entryForKey(String key) {
        KeyParts parts = KeyParts.parse(key);
        ObjectNode root = loadRoot(parts.objectPath());
        JsonNode entry = root.get(parts.targetVariable());
        return entry instanceof ObjectNode objectNode ? objectNode : null;
    }

    private ObjectNode mutableEntryForKey(String key) {
        KeyParts parts = KeyParts.parse(key);
        ObjectNode root = loadRoot(parts.objectPath());
        JsonNode existing = root.get(parts.targetVariable());
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        root.set(parts.targetVariable(), created);
        return created;
    }

    private ObjectNode loadRoot(String objectPath) {
        return cacheByObjectPath.computeIfAbsent(objectPath, this::readRootFromObject);
    }

    private ObjectNode readRootFromObject(String objectPath) {
        PlatformObject node = objectManager.require(objectPath);
        return node.getVariable(BindingStateVariables.BINDING_STATE)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(json -> !json.isBlank())
                .map(this::parseRoot)
                .orElseGet(objectMapper::createObjectNode);
    }

    private ObjectNode parseRoot(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode objectNode) {
                return objectNode.deepCopy();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return objectMapper.createObjectNode();
    }

    private void persist(String key) {
        KeyParts parts = KeyParts.parse(key);
        ObjectNode root = cacheByObjectPath.get(parts.objectPath());
        if (root == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(root);
            DataRecord record = DataRecord.single(STRING_VALUE, Map.of("value", json));
            objectManager.upsertSystemVariable(
                    parts.objectPath(),
                    BindingStateVariables.BINDING_STATE,
                    STRING_VALUE,
                    record
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist binding state for " + parts.objectPath(), ex);
        }
    }

    private static ArrayNode samplesArray(ObjectNode entry) {
        JsonNode existing = entry.get("samples");
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = entry.arrayNode();
        entry.set("samples", created);
        return created;
    }

    private static void appendSample(ArrayNode samples, long timestampMs, double value, long windowMs) {
        ObjectNode sample = samples.objectNode();
        sample.put("t", timestampMs);
        sample.put("v", value);
        samples.add(sample);
        long cutoff = timestampMs - windowMs;
        for (int i = samples.size() - 1; i >= 0; i--) {
            if (samples.get(i).get("t").asLong() < cutoff) {
                samples.remove(i);
            }
        }
        while (samples.size() > MAX_TIMED_SAMPLES) {
            samples.remove(0);
        }
    }

    private record KeyParts(String objectPath, String targetVariable) {
        static KeyParts parse(String key) {
            int separator = key.indexOf('|');
            if (separator <= 0 || separator >= key.length() - 1) {
                throw new IllegalArgumentException("Invalid binding state key: " + key);
            }
            return new KeyParts(key.substring(0, separator), key.substring(separator + 1));
        }
    }
}
