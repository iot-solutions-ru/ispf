package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.persistence.ObjectEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectVariableServiceProcessProgramTargetTest {

    private static final String PATH = "root.platform.process-programs.pp-proof";
    private static final String TARGET = "root.platform.devices.tgt-proof";
    private static final DataSchema STRING = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectEntityMapper mapper;

    private ObjectTree tree;
    private PlatformObject program;
    private ObjectVariableService variableService;

    @BeforeEach
    void setUp() {
        tree = new ObjectTree();
        program = new PlatformObject(
                "pp-proof", PATH, ObjectType.PROCESS_PROGRAM, "pp-proof", "", null);
        program.addVariable(new Variable("targetObjectPath", STRING, true, true, null));
        program.addVariable(new Variable("outputVariable", STRING, true, true, null));
        tree.register(program);
        PlatformObject target = new PlatformObject(
                "tgt-proof", TARGET, ObjectType.DEVICE, "tgt-proof", "", null);
        target.addVariable(new Variable("temperature", STRING, true, true, null));
        tree.register(target);
        when(objectManager.tree()).thenReturn(tree);
        lenient().when(mapper.auditDiff(any(), any())).thenReturn("{}");
        variableService = new ObjectVariableService(
                objectManager, null, mapper, null, null, null, null, null
        );
    }

    @Test
    void missingProcessProgramTargetIsRejectedBeforeSave() {
        assertThatThrownBy(() -> variableService.setVariableValue(
                PATH,
                "targetObjectPath",
                DataRecord.single(STRING, Map.of("value", "root.platform.devices.tgt-missing"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown object: root.platform.devices.tgt-missing");
        assertThat(program.getVariable("targetObjectPath").flatMap(Variable::value)).isEmpty();
        verify(objectManager, never()).persistVariable(any(), any());
    }

    @Test
    void existingTargetAndBlankValuesAreAccepted() {
        assertThatCode(() -> variableService.setVariableValue(
                PATH,
                "targetObjectPath",
                DataRecord.single(STRING, Map.of("value", TARGET))))
                .doesNotThrowAnyException();
        assertThat(program.getVariable("targetObjectPath").flatMap(Variable::value)).isPresent();

        assertThatCode(() -> variableService.setVariableValue(
                PATH,
                "outputVariable",
                DataRecord.single(STRING, Map.of("value", "temperature"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> variableService.setVariableValue(
                PATH,
                "outputVariable",
                DataRecord.single(STRING, Map.of("value", "noSuchOut"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> variableService.setVariableValue(
                PATH,
                "targetObjectPath",
                DataRecord.single(STRING, Map.of("value", ""))))
                .doesNotThrowAnyException();
        verify(objectManager, atLeastOnce()).persistVariable(any(), any());
    }

    @Test
    void retargetDoesNotRequireStoredOutputOnNewObject() {
        program.getVariable("outputVariable").orElseThrow()
                .setValue(DataRecord.single(STRING, Map.of("value", "temperature")));
        PlatformObject other = new PlatformObject(
                "tgt-other", "root.platform.devices.tgt-other", ObjectType.DEVICE, "tgt-other", "", null);
        other.addVariable(new Variable("setpoint", STRING, true, true, null));
        tree.register(other);

        assertThatCode(() -> variableService.setVariableValue(
                PATH,
                "targetObjectPath",
                DataRecord.single(STRING, Map.of("value", "root.platform.devices.tgt-other"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> variableService.setVariableValue(
                PATH,
                "outputVariable",
                DataRecord.single(STRING, Map.of("value", "setpoint"))))
                .doesNotThrowAnyException();
    }
}
