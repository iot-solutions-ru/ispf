package com.ispf.server.plugin.blueprint;

import tools.jackson.databind.ObjectMapper;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.plugin.blueprint.dto.BlueprintDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class BlueprintPersistenceService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintEngine blueprintEngine;

    public BlueprintPersistenceService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            BlueprintRegistry blueprintRegistry,
            BlueprintEngine blueprintEngine
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintEngine = blueprintEngine;
    }

    public void persist(BlueprintDefinition model, boolean builtin) {
        try {
            String json = objectMapper.writeValueAsString(BlueprintDto.from(model));
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM blueprint_definitions WHERE id = ?",
                    Integer.class,
                    model.id()
            );
            if (count != null && count > 0) {
                jdbcTemplate.update("""
                        UPDATE blueprint_definitions
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
                    INSERT INTO blueprint_definitions (id, name, definition_json, builtin, updated_at)
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
        jdbcTemplate.update("DELETE FROM blueprint_definitions WHERE id = ? AND builtin = FALSE", modelId);
    }

    public void restoreCustomBlueprints() {
        List<String> jsonRows = jdbcTemplate.queryForList(
                "SELECT definition_json FROM blueprint_definitions WHERE builtin = FALSE",
                String.class
        );
        for (String json : jsonRows) {
            try {
                BlueprintDto dto = objectMapper.readValue(json, BlueprintDto.class);
                if (blueprintRegistry.findByName(dto.name()).isPresent()) {
                    continue;
                }
                BlueprintDefinition model = new BlueprintDefinition(
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
                blueprintRegistry.register(model);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to restore model from persistence", ex);
            }
        }
    }
}
