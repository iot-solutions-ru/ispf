package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEventTriggerIndexTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String WORKFLOWS_ROOT = "root.platform.workflows";

    @Mock
    private com.ispf.server.object.ObjectManager objectManager;

    private WorkflowEventTriggerIndex index;

    @BeforeEach
    void setUp() {
        index = new WorkflowEventTriggerIndex(objectManager, OBJECT_MAPPER);
    }

    @Test
    void parseTriggerSupportsExplicitEventType() {
        var binding = WorkflowEventTriggerIndex.parseTrigger(
                "root.platform.workflows.alarm",
                """
                        {"triggerType":"event","objectPath":"root.device","eventName":"thresholdExceeded"}
                        """,
                OBJECT_MAPPER
        );
        assertTrue(binding.isPresent());
        assertEquals(WorkflowEventTriggerIndex.TriggerType.EVENT, binding.get().triggerType());
        assertEquals("root.device", binding.get().objectPath());
        assertEquals("thresholdExceeded", binding.get().eventName());
    }

    @Test
    void parseTriggerSupportsLegacyVariableShape() {
        var binding = WorkflowEventTriggerIndex.parseTrigger(
                "root.platform.workflows.alarm",
                """
                        {"objectPath":"root.device","variableName":"alarmActive","expectedValue":true}
                        """,
                OBJECT_MAPPER
        );
        assertTrue(binding.isPresent());
        assertEquals(WorkflowEventTriggerIndex.TriggerType.VARIABLE, binding.get().triggerType());
        assertEquals("alarmActive", binding.get().variableName());
    }

    @Test
    void parseTriggerSupportsExplicitVariableType() {
        var binding = WorkflowEventTriggerIndex.parseTrigger(
                "root.platform.workflows.alarm",
                """
                        {"triggerType":"variable","objectPath":"root.device","variableName":"alarmActive"}
                        """,
                OBJECT_MAPPER
        );
        assertTrue(binding.isPresent());
        assertEquals(WorkflowEventTriggerIndex.TriggerType.VARIABLE, binding.get().triggerType());
    }

    @Test
    void invalidateClearsLookupsUntilRebuild() {
        index.invalidate();
        assertEquals(List.of(), index.findEventWorkflows("root.device", "alarm"));
        assertEquals(List.of(), index.findVariableWorkflows("root.device", "alarmActive"));
    }

    @Test
    void removeWorkflowDropsPathFromEventAndVariableMaps() {
        PlatformObject eventWorkflow = workflowNode(
                "root.platform.workflows.stale-event",
                WorkflowLifecycleStatus.ACTIVE,
                """
                        {"triggerType":"event","objectPath":"root.device","eventName":"thresholdExceeded"}
                        """
        );
        PlatformObject keepWorkflow = workflowNode(
                "root.platform.workflows.keep-event",
                WorkflowLifecycleStatus.ACTIVE,
                """
                        {"triggerType":"event","objectPath":"root.device","eventName":"thresholdExceeded"}
                        """
        );
        PlatformObject variableWorkflow = workflowNode(
                "root.platform.workflows.stale-var",
                WorkflowLifecycleStatus.ACTIVE,
                """
                        {"triggerType":"variable","objectPath":"root.device","variableName":"temperature"}
                        """
        );
        when(objectManager.tree()).thenReturn(treeWithChildren(eventWorkflow, keepWorkflow, variableWorkflow));

        index.rebuild();
        assertEquals(
                List.of("root.platform.workflows.keep-event", "root.platform.workflows.stale-event"),
                index.findEventWorkflows("root.device", "thresholdExceeded").stream().sorted().toList()
        );
        assertEquals(
                List.of("root.platform.workflows.stale-var"),
                index.findVariableWorkflows("root.device", "temperature")
        );

        index.removeWorkflow("root.platform.workflows.stale-event");
        index.removeWorkflow("root.platform.workflows.stale-var");

        assertEquals(
                List.of("root.platform.workflows.keep-event"),
                index.findEventWorkflows("root.device", "thresholdExceeded")
        );
        assertEquals(List.of(), index.findVariableWorkflows("root.device", "temperature"));
    }

    private static com.ispf.core.object.ObjectTree treeWithChildren(PlatformObject... workflows) {
        com.ispf.core.object.ObjectTree tree = new com.ispf.core.object.ObjectTree();
        PlatformObject root = new PlatformObject(
                "workflows-root",
                WORKFLOWS_ROOT,
                ObjectType.WORKFLOWS,
                "Workflows",
                "",
                null
        );
        tree.register(root);
        for (PlatformObject workflow : workflows) {
            tree.register(workflow);
        }
        return tree;
    }

    private static PlatformObject workflowNode(
            String path,
            WorkflowLifecycleStatus status,
            String triggerJson
    ) {
        PlatformObject node = new PlatformObject(
                path,
                path,
                ObjectType.WORKFLOW,
                "Workflow",
                "",
                "workflow-v1"
        );
        node.addVariable(new com.ispf.core.object.Variable(
                "status",
                DataSchema.builder("status").field("value", FieldType.STRING).build(),
                true,
                true,
                DataRecord.single(
                        DataSchema.builder("status").field("value", FieldType.STRING).build(),
                        Map.of("value", status.name())
                )
        ));
        node.addVariable(new com.ispf.core.object.Variable(
                "triggerJson",
                DataSchema.builder("triggerJson").field("value", FieldType.STRING).build(),
                true,
                true,
                DataRecord.single(
                        DataSchema.builder("triggerJson").field("value", FieldType.STRING).build(),
                        Map.of("value", triggerJson.trim())
                )
        ));
        return node;
    }
}
