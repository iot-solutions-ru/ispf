package com.ispf.server.process;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProcessProgramRunnerTest {

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private static final DataSchema INTEGER_SCHEMA = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();

    private static final DataSchema BOOLEAN_SCHEMA = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    @Autowired
    private ProcessProgramRunner processProgramRunner;

    @Autowired
    private ProcessProgramObjectService processProgramObjectService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private SystemObjectStructureService structureService;

    @Test
    @Transactional
    void evaluatesEnabledProgramEachCycle() {
        processProgramObjectService.ensureCatalog();
        String path = ProcessProgramPaths.PROCESS_PROGRAMS_ROOT + ".test-loop";
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    ProcessProgramPaths.PROCESS_PROGRAMS_ROOT,
                    "test-loop",
                    ObjectType.PROCESS_PROGRAM,
                    "Test loop",
                    "Process program test",
                    null
            );
        }
        structureService.ensureProcessProgramStructure(path);
        objectManager.setVariableValue(path, "programId", DataRecord.single(STRING_SCHEMA, Map.of("value", "test-loop")));
        objectManager.setVariableValue(path, "cycleIntervalMs", DataRecord.single(INTEGER_SCHEMA, Map.of("value", 1)));
        objectManager.setVariableValue(path, "controlExpression", DataRecord.single(STRING_SCHEMA, Map.of("value", "true")));
        objectManager.setVariableValue(path, "enabled", DataRecord.single(BOOLEAN_SCHEMA, Map.of("value", true)));

        processProgramRunner.runDuePrograms();

        String lastCycleAt = objectManager.require(path).getVariable("lastCycleAt")
                .flatMap(v -> v.value().map(r -> String.valueOf(r.firstRow().get("value"))))
                .orElse("");
        assertThat(lastCycleAt).isNotBlank();
        assertThat(Instant.parse(lastCycleAt)).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @Transactional
    void closedLoopWritesActuatorWhenInterlockPasses() {
        processProgramObjectService.ensureCatalog();
        String plantPath = "root.platform.devices.process-loop-plant";
        if (objectManager.tree().findByPath(plantPath).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "process-loop-plant",
                    ObjectType.DEVICE,
                    "Process loop plant",
                    "BL-172 closed-loop target",
                    null
            );
        }
        DataSchema doubleSchema = DataSchema.builder("doubleValue").field("value", FieldType.DOUBLE).build();
        ensureVar(plantPath, "pv", doubleSchema, 10.0d);
        ensureVar(plantPath, "sp", doubleSchema, 0.0d);
        ensureVar(plantPath, "mode", STRING_SCHEMA, "AUTO");

        String path = ProcessProgramPaths.PROCESS_PROGRAMS_ROOT + ".closed-loop";
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    ProcessProgramPaths.PROCESS_PROGRAMS_ROOT,
                    "closed-loop",
                    ObjectType.PROCESS_PROGRAM,
                    "Closed loop",
                    "Writes SP from PV",
                    null
            );
        }
        structureService.ensureProcessProgramStructure(path);
        objectManager.setVariableValue(path, "programId", DataRecord.single(STRING_SCHEMA, Map.of("value", "closed-loop")));
        objectManager.setVariableValue(path, "cycleIntervalMs", DataRecord.single(INTEGER_SCHEMA, Map.of("value", 1)));
        objectManager.setVariableValue(path, "targetObjectPath", DataRecord.single(STRING_SCHEMA, Map.of("value", plantPath)));
        objectManager.setVariableValue(path, "outputVariable", DataRecord.single(STRING_SCHEMA, Map.of("value", "sp")));
        objectManager.setVariableValue(path, "controlExpression", DataRecord.single(STRING_SCHEMA, Map.of("value", "self.pv.value * 2.0")));
        objectManager.setVariableValue(path, "interlockExpression", DataRecord.single(STRING_SCHEMA, Map.of("value", "self.mode.value == \"AUTO\"")));
        objectManager.setVariableValue(path, "enabled", DataRecord.single(BOOLEAN_SCHEMA, Map.of("value", true)));

        processProgramRunner.runDuePrograms();

        double sp = objectManager.require(plantPath).getVariable("sp")
                .flatMap(v -> v.value().map(r -> ((Number) r.firstRow().get("value")).doubleValue()))
                .orElse(-1.0d);
        assertThat(sp).isEqualTo(20.0d);
        String lastOutput = objectManager.require(path).getVariable("lastOutput")
                .flatMap(v -> v.value().map(r -> String.valueOf(r.firstRow().get("value"))))
                .orElse("");
        assertThat(lastOutput).contains("20");
        String lastError = objectManager.require(path).getVariable("lastError")
                .flatMap(v -> v.value().map(r -> String.valueOf(r.firstRow().get("value"))))
                .orElse("missing");
        assertThat(lastError).isBlank();
    }

    @Test
    @Transactional
    void interlockBlocksWrite() {
        processProgramObjectService.ensureCatalog();
        String plantPath = "root.platform.devices.process-loop-plant-manual";
        if (objectManager.tree().findByPath(plantPath).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "process-loop-plant-manual",
                    ObjectType.DEVICE,
                    "Manual plant",
                    "BL-172 interlock",
                    null
            );
        }
        DataSchema doubleSchema = DataSchema.builder("doubleValue").field("value", FieldType.DOUBLE).build();
        ensureVar(plantPath, "sp", doubleSchema, 1.0d);
        ensureVar(plantPath, "mode", STRING_SCHEMA, "MANUAL");

        String path = ProcessProgramPaths.PROCESS_PROGRAMS_ROOT + ".interlock-block";
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    ProcessProgramPaths.PROCESS_PROGRAMS_ROOT,
                    "interlock-block",
                    ObjectType.PROCESS_PROGRAM,
                    "Interlock block",
                    "Should not write",
                    null
            );
        }
        structureService.ensureProcessProgramStructure(path);
        objectManager.setVariableValue(path, "programId", DataRecord.single(STRING_SCHEMA, Map.of("value", "interlock-block")));
        objectManager.setVariableValue(path, "cycleIntervalMs", DataRecord.single(INTEGER_SCHEMA, Map.of("value", 1)));
        objectManager.setVariableValue(path, "targetObjectPath", DataRecord.single(STRING_SCHEMA, Map.of("value", plantPath)));
        objectManager.setVariableValue(path, "outputVariable", DataRecord.single(STRING_SCHEMA, Map.of("value", "sp")));
        objectManager.setVariableValue(path, "controlExpression", DataRecord.single(STRING_SCHEMA, Map.of("value", "99.0")));
        objectManager.setVariableValue(path, "interlockExpression", DataRecord.single(STRING_SCHEMA, Map.of("value", "self.mode.value == \"AUTO\"")));
        objectManager.setVariableValue(path, "enabled", DataRecord.single(BOOLEAN_SCHEMA, Map.of("value", true)));

        processProgramRunner.runDuePrograms();

        double sp = objectManager.require(plantPath).getVariable("sp")
                .flatMap(v -> v.value().map(r -> ((Number) r.firstRow().get("value")).doubleValue()))
                .orElse(-1.0d);
        assertThat(sp).isEqualTo(1.0d);
        String lastError = objectManager.require(path).getVariable("lastError")
                .flatMap(v -> v.value().map(r -> String.valueOf(r.firstRow().get("value"))))
                .orElse("");
        assertThat(lastError).contains("interlock");
    }

    private void ensureVar(String path, String name, DataSchema schema, Object value) {
        var node = objectManager.require(path);
        if (node.getVariable(name).isEmpty()) {
            node.addVariable(new Variable(name, schema, true, true, DataRecord.single(schema, Map.of("value", value))));
            objectManager.persistNodeTree(path);
        } else {
            objectManager.setVariableValue(path, name, DataRecord.single(schema, Map.of("value", value)));
        }
    }
}
