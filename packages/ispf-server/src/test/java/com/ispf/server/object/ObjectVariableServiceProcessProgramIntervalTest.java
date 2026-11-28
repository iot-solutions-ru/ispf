package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectVariableServiceProcessProgramIntervalTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformObject program;

    @Test
    void nonPositiveCycleIntervalIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.require("root.platform.process-programs.pp-proof")).thenReturn(program);
        when(program.type()).thenReturn(ObjectType.PROCESS_PROGRAM);
        ObjectVariableService service = new ObjectVariableService(
                objectManager, null, null, null, null, null, null, null
        );

        IllegalArgumentException negative = assertThrows(
                IllegalArgumentException.class,
                () -> service.setVariableValue(
                        "root.platform.process-programs.pp-proof",
                        "cycleIntervalMs",
                        record(-3)
                )
        );
        IllegalArgumentException zero = assertThrows(
                IllegalArgumentException.class,
                () -> service.setVariableValue(
                        "root.platform.process-programs.pp-proof",
                        "cycleIntervalMs",
                        record(0)
                )
        );

        assertEquals("cycleIntervalMs must be positive", negative.getMessage());
        assertEquals("cycleIntervalMs must be positive", zero.getMessage());
        verify(program, never()).setVariableValue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(objectManager, never()).persistVariable(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static DataRecord record(int cycleIntervalMs) {
        DataSchema schema = DataSchema.builder("cycleIntervalMs")
                .field("value", FieldType.INTEGER)
                .build();
        return DataRecord.single(schema, Map.of("value", cycleIntervalMs));
    }
}
