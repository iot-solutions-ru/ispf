package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.workflow.WorkflowRuntimeFields;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectVariableServiceWorkflowRuntimeFieldsTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformObject workflow;

    @Test
    void unreadableRuntimeFieldsAreRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.require("root.platform.workflows.runtime-proof")).thenReturn(workflow);
        when(workflow.type()).thenReturn(ObjectType.WORKFLOW);
        ObjectVariableService service = new ObjectVariableService(
                objectManager, null, null, null, null, null, null, null
        );

        IllegalArgumentException state = assertThrows(
                IllegalArgumentException.class,
                () -> service.setVariableValue(
                        "root.platform.workflows.runtime-proof",
                        "instanceState",
                        text("instanceState", "{")
                )
        );
        IllegalArgumentException stamp = assertThrows(
                IllegalArgumentException.class,
                () -> service.setVariableValue(
                        "root.platform.workflows.runtime-proof",
                        "lastRunAt",
                        text("lastRunAt", "yesterday")
                )
        );

        assertEquals("Invalid instanceState", state.getMessage());
        assertEquals("Invalid lastRunAt", stamp.getMessage());
        verify(workflow, never()).setVariableValue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(objectManager, never()).persistVariable(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blankAndReadableRuntimeFieldsStayValid() {
        WorkflowRuntimeFields.requireReadable("instanceState", "");
        WorkflowRuntimeFields.requireReadable("instanceState", "  ");
        WorkflowRuntimeFields.requireReadable("instanceState", "{}");
        WorkflowRuntimeFields.requireReadable("lastRunAt", "");
        WorkflowRuntimeFields.requireReadable("lastRunAt", Instant.parse("2026-11-25T12:00:00Z").toString());
    }

    private static DataRecord text(String name, String value) {
        DataSchema schema = DataSchema.builder(name)
                .field("value", FieldType.STRING)
                .build();
        return DataRecord.single(schema, Map.of("value", value));
    }
}
