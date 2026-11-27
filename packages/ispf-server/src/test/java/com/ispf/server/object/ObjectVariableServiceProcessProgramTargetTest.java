package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ObjectVariableServiceProcessProgramTargetTest {

    private static final String PATH = "root.platform.process-programs.pp-proof";
    private static final DataSchema STRING = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    @Mock
    private ObjectManager objectManager;

    private ObjectVariableService variableService;

    @BeforeEach
    void setUp() {
        ObjectTree tree = new ObjectTree();
        PlatformObject program = new PlatformObject(
                "pp-proof", PATH, ObjectType.PROCESS_PROGRAM, "pp-proof", "", null);
        program.addVariable(new Variable("targetObjectPath", STRING, true, true, null));
        program.addVariable(new Variable("outputVariable", STRING, true, true, null));
        tree.register(program);
        lenient().when(objectManager.tree()).thenReturn(tree);
        variableService = new ObjectVariableService(
                objectManager, null, null, null, null, null, null, null
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
        assertThatThrownBy(() -> variableService.setVariableValue(
                PATH,
                "outputVariable",
                DataRecord.single(STRING, Map.of("value", "noSuchOut"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown variable: noSuchOut");
        verify(objectManager, never()).persistVariable(any(), any());
    }
}
