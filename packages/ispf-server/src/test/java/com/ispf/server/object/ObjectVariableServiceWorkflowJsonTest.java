package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.workflow.WorkflowStoredJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectVariableServiceWorkflowJsonTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformObject workflow;

    @Test
    void unreadableWorkflowJsonIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.require("root.platform.workflows.trigger-proof")).thenReturn(workflow);
        when(workflow.type()).thenReturn(ObjectType.WORKFLOW);
        ObjectVariableService service = new ObjectVariableService(
                objectManager, null, null, null, null, null, null, null
        );

        assertRejected(service, "triggerJson");
        assertRejected(service, "inputSchemaJson");
        assertRejected(service, "outputSchemaJson");
        verify(workflow, never()).setVariableValue(any(), any());
        verify(objectManager, never()).persistVariable(any(), any());
    }

    @Test
    void emptyJsonObjectIsReadable() {
        PlatformObject node = org.mockito.Mockito.mock(PlatformObject.class);
        when(node.type()).thenReturn(ObjectType.WORKFLOW);
        WorkflowStoredJson.rejectUnreadable(node, "triggerJson", record("triggerJson", "{}"));
        WorkflowStoredJson.rejectUnreadable(node, "inputSchemaJson", record("inputSchemaJson", "{}"));
        WorkflowStoredJson.rejectUnreadable(node, "outputSchemaJson", record("outputSchemaJson", "{}"));
    }

    private static void assertRejected(ObjectVariableService service, String name) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.setVariableValue(
                        "root.platform.workflows.trigger-proof",
                        name,
                        record(name, "{")
                )
        );
        assertEquals("Invalid " + name, ex.getMessage());
    }

    private static DataRecord record(String name, String json) {
        DataSchema schema = DataSchema.builder(name)
                .field("value", FieldType.STRING)
                .build();
        return DataRecord.single(schema, Map.of("value", json));
    }
}
