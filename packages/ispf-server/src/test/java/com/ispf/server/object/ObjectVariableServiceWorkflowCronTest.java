package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.workflow.WorkflowCronDue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectVariableServiceWorkflowCronTest {

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ObjectTree objectTree;

    @Mock
    private PlatformObject workflow;

    @Test
    void invalidCronIsRejectedBeforeSave() {
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.require("root.platform.workflows.cron-proof")).thenReturn(workflow);
        when(workflow.type()).thenReturn(ObjectType.WORKFLOW);
        ObjectVariableService service = new ObjectVariableService(
                objectManager, null, null, null, null, null, null, null
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.setVariableValue(
                        "root.platform.workflows.cron-proof",
                        "cronExpression",
                        record("not a cron")
                )
        );

        assertTrue(ex.getMessage().startsWith("Invalid workflow cronExpression"));
        assertEquals(
                "Invalid workflow cronExpression (use every:Nm or 5/6-field cron): not a cron",
                ex.getMessage()
        );
        verify(workflow, never()).setVariableValue(any(), any());
        verify(objectManager, never()).persistVariable(any(), any());
    }

    @Test
    void blankAndEveryMinuteCronAreAccepted() {
        WorkflowCronDue.requireValid("");
        WorkflowCronDue.requireValid("  ");
        WorkflowCronDue.requireValid("every:5m");
        WorkflowCronDue.requireValid("0 * * * *");
    }

    private static DataRecord record(String cron) {
        DataSchema schema = DataSchema.builder("cronExpression")
                .field("value", FieldType.STRING)
                .build();
        return DataRecord.single(schema, Map.of("value", cron));
    }
}
