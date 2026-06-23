package com.ispf.server.plugin.model;

import tools.jackson.databind.ObjectMapper;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.plugin.model.dto.ModelDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class ModelPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;

    public ModelPersistenceService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
    }

    public void persist(ModelDefinition model, boolean builtin) {
        try {
            String json = objectMapper.writeValueAsString(ModelDto.from(model));
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM model_definitions WHERE id = ?",
                    Integer.class,
                    model.id()
            );
            if (count != null && count > 0) {
                jdbcTemplate.update("""
                        UPDATE model_definitions
                        SET name = ?, definition_json = ?, builtin = ?, updated_at = ?
                        WHERE id = ?
                        """,
                        model.name(),
                        json,
                        builtin,
                        Timestamp.from(Instant.now()),
                        model.id()
                );
                return;
            }
            jdbcTemplate.update("""
                    INSERT INTO model_definitions (id, name, definition_json, builtin, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    model.id(),
                    model.name(),
                    json,
                    builtin,
                    Timestamp.from(Instant.now())
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist model " + model.name(), ex);
        }
    }

    public void delete(String modelId) {
        jdbcTemplate.update("DELETE FROM model_definitions WHERE id = ? AND builtin = FALSE", modelId);
    }

    public void restoreCustomModels() {
        List<String> jsonRows = jdbcTemplate.queryForList(
                "SELECT definition_json FROM model_definitions WHERE builtin = FALSE",
                String.class
        );
        for (String json : jsonRows) {
            try {
                ModelDto dto = objectMapper.readValue(json, ModelDto.class);
                if (modelRegistry.findByName(dto.name()).isPresent()) {
                    continue;
                }
                ModelDefinition model = new ModelDefinition(
                        dto.id(),
                        dto.name(),
                        dto.description(),
                        dto.type(),
                        dto.targetObjectType(),
                        dto.suitabilityExpression(),
                        dto.variables(),
                        dto.events(),
                        dto.functions(),
                        dto.bindings(),
                        dto.parameters(),
                        dto.createdAt(),
                        dto.updatedAt()
                );
                modelRegistry.register(model);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to restore model from persistence", ex);
            }
        }
    }
}
