package com.ispf.server.workflow;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
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
class WorkflowTriggerIndexTest {

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
    void rebuildIndexesActiveVariableTriggers() {
        PlatformObject workflow = workflowNode(
                "root.platform.workflows.temp-alarm",
                WorkflowLifecycleStatus.ACTIVE,
                """
                        {"triggerType":"variable","objectPath":"root.device","variableName":"temperature"}
                        """
        );
        when(objectManager.tree()).thenReturn(treeWithChildren(workflow));

        index.rebuild();

        assertEquals(1, index.variableTriggersIndexed());
        assertEquals(List.of("root.platform.workflows.temp-alarm"),
                index.findVariableWorkflows("root.device", "temperature"));
    }

    @Test
    void rebuildSkipsDraftWorkflows() {
        PlatformObject workflow = workflowNode(
                "root.platform.workflows.temp-alarm",
                WorkflowLifecycleStatus.DRAFT,
                """
                        {"triggerType":"variable","objectPath":"root.device","variableName":"temperature"}
                        """
        );
        when(objectManager.tree()).thenReturn(treeWithChildren(workflow));

        index.rebuild();

        assertEquals(0, index.variableTriggersIndexed());
        assertTrue(index.findVariableWorkflows("root.device", "temperature").isEmpty());
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
