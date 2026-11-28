package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.application.script.FunctionScriptValidator;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectVariableServiceProcessProgramExpressionTest {

    private static final String PATH = "root.platform.process-programs.pp-proof";
    private static final String OTHER = "root.platform.devices.other-proof";
    private static final DataSchema STRING = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectEntityMapper mapper;

    private PlatformObject program;
    private ObjectVariableService variableService;

    @BeforeEach
    void setUp() {
        ObjectTree tree = new ObjectTree();
        program = new PlatformObject(
                "pp-proof", PATH, ObjectType.PROCESS_PROGRAM, "pp-proof", "", null);
        program.addVariable(new Variable("controlExpression", STRING, true, true, null));
        program.addVariable(new Variable("interlockExpression", STRING, true, true, null));
        tree.register(program);
        PlatformObject other = new PlatformObject(
                "other-proof", OTHER, ObjectType.DEVICE, "other-proof", "", null);
        other.addVariable(new Variable("controlExpression", STRING, true, true, null));
        tree.register(other);
        when(objectManager.tree()).thenReturn(tree);
        lenient().when(mapper.auditDiff(any(), any())).thenReturn("{}");
        variableService = new ObjectVariableService(
                objectManager,
                null,
                mapper,
                null,
                null,
                new FunctionScriptValidator(new ObjectMapper()),
                null,
                null
        );
    }

    @Test
    void unreadableProcessProgramExpressionIsRejectedBeforeSave() {
        assertThatThrownBy(() -> variableService.setVariableValue(
                PATH, "controlExpression", DataRecord.single(STRING, Map.of("value", "1 +"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("controlExpression:");
        assertThat(program.getVariable("controlExpression").flatMap(Variable::value)).isEmpty();
        assertThatThrownBy(() -> variableService.setVariableValue(
                PATH, "interlockExpression", DataRecord.single(STRING, Map.of("value", "{"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interlockExpression:");
        assertThat(program.getVariable("interlockExpression").flatMap(Variable::value)).isEmpty();
        verify(objectManager, never()).persistVariable(any(), any());
    }

    @Test
    void readableCelAndPlatformBindingsAreAccepted() {
        assertThatCode(() -> variableService.setVariableValue(
                PATH, "controlExpression", DataRecord.single(STRING, Map.of("value", "true"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> variableService.setVariableValue(
                PATH, "interlockExpression",
                DataRecord.single(STRING, Map.of("value", "self.pv.value * 2.0"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> variableService.setVariableValue(
                PATH, "controlExpression",
                DataRecord.single(STRING, Map.of("value", "clamp(temperature, 0, 50)"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> variableService.setVariableValue(
                PATH, "controlExpression",
                DataRecord.single(STRING, Map.of("value", " read(\"root.platform.devices.foo/temperature\") "))))
                .doesNotThrowAnyException();
        verify(objectManager, org.mockito.Mockito.atLeastOnce()).persistVariable(any(), any());
    }

    @Test
    void blankAndWhitespaceExpressionsAreAccepted() {
        assertThatCode(() -> variableService.setVariableValue(
                PATH, "controlExpression", DataRecord.single(STRING, Map.of("value", ""))))
                .doesNotThrowAnyException();
        assertThatCode(() -> variableService.setVariableValue(
                PATH, "interlockExpression", DataRecord.single(STRING, Map.of("value", "   "))))
                .doesNotThrowAnyException();
    }

    @Test
    void sameVariableOnOtherObjectTypeIsNotValidated() {
        assertThatCode(() -> variableService.setVariableValue(
                OTHER, "controlExpression", DataRecord.single(STRING, Map.of("value", "1 +"))))
                .doesNotThrowAnyException();
    }
}
