package com.ispf.server.workflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class WorkflowEventTriggerIndexTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
}
