package com.ispf.server.process;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.config.ClusterProperties;
import com.ispf.server.expression.ExpressionEvaluationService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.PlatformLeaderLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Cyclic scheduler for PROCESS_PROGRAM objects (BL-172).
 * Evaluates {@code controlExpression} against {@code targetObjectPath} and writes the result
 * to {@code outputVariable} when the optional interlock passes.
 */
@Component
public class ProcessProgramRunner {

    private static final String LOCK_NAME = "process_program_runner";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();
    private static final DataSchema BOOLEAN_VALUE = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();
    private static final DataSchema INTEGER_VALUE = DataSchema.builder("integerValue")
            .field("value", FieldType.INTEGER)
            .build();
    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    private final ProcessProgramObjectService processProgramObjectService;
    private final ExpressionEvaluationService expressionEvaluationService;
    private final ObjectManager objectManager;
    private final PlatformLeaderLockService leaderLockService;
    private final ClusterProperties clusterProperties;

    public ProcessProgramRunner(
            ProcessProgramObjectService processProgramObjectService,
            ExpressionEvaluationService expressionEvaluationService,
            ObjectManager objectManager,
            PlatformLeaderLockService leaderLockService,
            ClusterProperties clusterProperties
    ) {
        this.processProgramObjectService = processProgramObjectService;
        this.expressionEvaluationService = expressionEvaluationService;
        this.objectManager = objectManager;
        this.leaderLockService = leaderLockService;
        this.clusterProperties = clusterProperties;
    }

    @Scheduled(fixedDelayString = "${ispf.process-program.tick-ms:1000}")
    public void tick() {
        if (!clusterProperties.isSchedulerActive()) {
            return;
        }
        if (!leaderLockService.tryAcquire(LOCK_NAME, LOCK_TTL)) {
            return;
        }
        try {
            runDuePrograms();
        } finally {
            leaderLockService.release(LOCK_NAME);
        }
    }

    void runDuePrograms() {
        Instant now = Instant.now();
        for (ProcessProgramObjectService.ProcessProgramDefinition program : processProgramObjectService.listEnabled()) {
            if (program.lastCycleAt() != null
                    && program.lastCycleAt().plusMillis(program.cycleIntervalMs()).isAfter(now)) {
                continue;
            }
            runProgram(program, now);
        }
    }

    private void runProgram(ProcessProgramObjectService.ProcessProgramDefinition program, Instant now) {
        String expression = program.controlExpression();
        if (expression == null || expression.isBlank()) {
            processProgramObjectService.recordCycle(program.path(), now, null);
            return;
        }
        String contextPath = program.targetObjectPath() != null && !program.targetObjectPath().isBlank()
                ? program.targetObjectPath().trim()
                : program.path();
        try {
            if (!interlockAllows(program, contextPath)) {
                processProgramObjectService.recordCycle(program.path(), now, "interlock blocked");
                return;
            }
            ExpressionEvaluationService.EvaluateResult result = expressionEvaluationService.evaluate(
                    contextPath,
                    expression,
                    null
            );
            if (!result.valid()) {
                processProgramObjectService.recordCycle(program.path(), now, result.error());
                return;
            }
            String output = stringify(result.result());
            if (program.outputVariable() != null && !program.outputVariable().isBlank()) {
                writeOutput(contextPath, program.outputVariable().trim(), result.result());
            }
            processProgramObjectService.recordCycle(program.path(), now, null, output);
        } catch (Exception ex) {
            processProgramObjectService.recordCycle(program.path(), now, ex.getMessage());
        }
    }

    private boolean interlockAllows(
            ProcessProgramObjectService.ProcessProgramDefinition program,
            String contextPath
    ) {
        String interlock = program.interlockExpression();
        if (interlock == null || interlock.isBlank()) {
            return true;
        }
        ExpressionEvaluationService.EvaluateResult result = expressionEvaluationService.evaluate(
                contextPath,
                interlock,
                null
        );
        if (!result.valid()) {
            throw new IllegalStateException("interlock error: " + result.error());
        }
        Object value = result.result();
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private void writeOutput(String objectPath, String variableName, Object rawValue) {
        PlatformObject node = objectManager.require(objectPath);
        DataSchema schema = node.getVariable(variableName)
                .map(Variable::schema)
                .orElse(STRING_VALUE);
        objectManager.setVariableValue(objectPath, variableName, toRecord(schema, rawValue));
    }

    private static DataRecord toRecord(DataSchema schema, Object rawValue) {
        FieldType type = schema.fields().isEmpty()
                ? FieldType.STRING
                : schema.fields().get(0).type();
        Object coerced = coerce(type, rawValue);
        String fieldName = schema.fields().isEmpty() ? "value" : schema.fields().get(0).name();
        DataSchema writeSchema = switch (type) {
            case BOOLEAN -> BOOLEAN_VALUE;
            case INTEGER, LONG -> INTEGER_VALUE;
            case DOUBLE -> DOUBLE_VALUE;
            default -> schema.fields().isEmpty() ? STRING_VALUE : schema;
        };
        if (type == FieldType.STRING || schema.fields().isEmpty()) {
            return DataRecord.single(STRING_VALUE, Map.of("value", coerced == null ? "" : String.valueOf(coerced)));
        }
        return DataRecord.single(writeSchema, Map.of(fieldName, coerced));
    }

    private static Object coerce(FieldType type, Object rawValue) {
        if (rawValue == null) {
            return switch (type) {
                case BOOLEAN -> false;
                case INTEGER, LONG -> 0;
                case DOUBLE -> 0.0d;
                default -> "";
            };
        }
        return switch (type) {
            case BOOLEAN -> rawValue instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(rawValue));
            case INTEGER, LONG -> rawValue instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(rawValue));
            case DOUBLE -> rawValue instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(rawValue));
            default -> String.valueOf(rawValue);
        };
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
