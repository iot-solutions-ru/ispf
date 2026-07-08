package com.ispf.server.process;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cyclic process-control program catalog (BL-172).
 */
@Service
public class ProcessProgramObjectService {

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema BOOLEAN_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;

    public ProcessProgramObjectService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
    }

    @Transactional
    public void ensureCatalog() {
        SystemObjectCatalogSupport.ensureFolder(
                objectManager,
                ProcessProgramPaths.PROCESS_PROGRAMS_ROOT,
                ObjectType.PROCESS_PROGRAMS,
                null
        );
    }

    /** Hot path from {@link ProcessProgramRunner} tick — read-only; catalog ensured at bootstrap. */
    @Transactional(readOnly = true)
    public List<ProcessProgramDefinition> listEnabled() {
        List<ProcessProgramDefinition> programs = new ArrayList<>();
        if (objectManager.tree().findByPath(ProcessProgramPaths.PROCESS_PROGRAMS_ROOT).isEmpty()) {
            return programs;
        }
        for (PlatformObject child : objectManager.tree().childrenOf(ProcessProgramPaths.PROCESS_PROGRAMS_ROOT)) {
            if (child.type() != ObjectType.PROCESS_PROGRAM) {
                continue;
            }
            toDefinition(child.path(), child).filter(ProcessProgramDefinition::enabled).ifPresent(programs::add);
        }
        return programs;
    }

    @Transactional
    public void recordCycle(String path, Instant at, String error) {
        ensureStructure(path);
        setString(path, "lastCycleAt", at != null ? at.toString() : "");
        setString(path, "lastError", error != null ? error : "");
    }

    private void ensureStructure(String path) {
        structureService.ensureProcessProgramStructure(path);
    }

    private Optional<ProcessProgramDefinition> toDefinition(String path, PlatformObject node) {
        return Optional.of(new ProcessProgramDefinition(
                path,
                readString(node, "programId").orElse(path.substring(path.lastIndexOf('.') + 1)),
                readInteger(node, "cycleIntervalMs").orElse(1000L),
                readString(node, "controlExpression").orElse(""),
                readBoolean(node, "enabled").orElse(false),
                readInstant(node, "lastCycleAt").orElse(null),
                readString(node, "lastError").orElse("")
        ));
    }

    private void setString(String path, String variable, String value) {
        objectManager.setVariableValue(path, variable, DataRecord.single(STRING_SCHEMA, Map.of("value", value != null ? value : "")));
    }

    private static Optional<String> readString(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> String.valueOf(r.firstRow().get("value")));
    }

    private static Optional<Boolean> readBoolean(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> {
            Object v = r.firstRow().get("value");
            return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
        });
    }

    private static Optional<Long> readInteger(PlatformObject node, String name) {
        return node.getVariable(name).flatMap(Variable::value).map(r -> {
            Object v = r.firstRow().get("value");
            if (v instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(v));
        });
    }

    private static Optional<Instant> readInstant(PlatformObject node, String name) {
        return readString(node, name)
                .filter(value -> !value.isBlank())
                .map(Instant::parse);
    }

    public record ProcessProgramDefinition(
            String path,
            String programId,
            long cycleIntervalMs,
            String controlExpression,
            boolean enabled,
            Instant lastCycleAt,
            String lastError
    ) {
    }
}
