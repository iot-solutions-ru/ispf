package com.ispf.plugin.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository of model definitions.
 */
public class ModelRegistry {

    private final Map<String, ModelDefinition> byId = new ConcurrentHashMap<>();
    private final Map<String, ModelDefinition> byName = new ConcurrentHashMap<>();

    public ModelDefinition register(ModelDefinition model) {
        if (byName.putIfAbsent(model.name(), model) != null) {
            throw new ModelException("Model already exists: " + model.name());
        }
        byId.put(model.id(), model);
        return model;
    }

    public ModelDefinition update(ModelDefinition model) {
        ModelDefinition existing = requireById(model.id());
        if (!existing.name().equals(model.name()) && byName.containsKey(model.name())) {
            throw new ModelException("Model name already taken: " + model.name());
        }
        byName.remove(existing.name());
        byName.put(model.name(), model);
        byId.put(model.id(), model);
        return model;
    }

    public void delete(String modelId) {
        ModelDefinition model = requireById(modelId);
        byId.remove(modelId);
        byName.remove(model.name());
    }

    public Optional<ModelDefinition> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<ModelDefinition> findByName(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<ModelDefinition> all() {
        return new ArrayList<>(byId.values());
    }

    public ModelDefinition requireById(String id) {
        return findById(id).orElseThrow(() -> new ModelException("Model not found: " + id));
    }

    public ModelDefinition requireByName(String name) {
        return findByName(name).orElseThrow(() -> new ModelException("Model not found: " + name));
    }
}
