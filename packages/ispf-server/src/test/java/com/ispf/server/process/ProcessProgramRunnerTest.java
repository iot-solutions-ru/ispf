package com.ispf.server.process;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
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
}
