package com.ispf.plugin.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository of model definitions.
 */
public class BlueprintRegistry {

    private final Map<String, BlueprintDefinition> byId = new ConcurrentHashMap<>();
    private final Map<String, BlueprintDefinition> byName = new ConcurrentHashMap<>();

    public BlueprintDefinition register(BlueprintDefinition model) {
        if (byName.putIfAbsent(model.name(), model) != null) {
            throw new BlueprintException("Model already exists: " + model.name());
        }
        byId.put(model.id(), model);
        return model;
    }

    public BlueprintDefinition update(BlueprintDefinition model) {
        BlueprintDefinition existing = requireById(model.id());
        if (!existing.name().equals(model.name()) && byName.containsKey(model.name())) {
            throw new BlueprintException("Model name already taken: " + model.name());
        }
        byName.remove(existing.name());
        byName.put(model.name(), model);
        byId.put(model.id(), model);
        return model;
    }

    public void delete(String modelId) {
        BlueprintDefinition model = requireById(modelId);
        byId.remove(modelId);
        byName.remove(model.name());
    }

    public Optional<BlueprintDefinition> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<BlueprintDefinition> findByName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<BlueprintDefinition> all() {
        return new ArrayList<>(byId.values());
    }

    public BlueprintDefinition requireById(String id) {
        return findById(id).orElseThrow(() -> new BlueprintException("Model not found: " + id));
    }

    public BlueprintDefinition requireByName(String name) {
        return findByName(name).orElseThrow(() -> new BlueprintException("Model not found: " + name));
    }
}
