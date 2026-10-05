package com.ispf.server.workflow;

import com.ispf.plugin.workflow.WorkflowException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowToolOutputTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unreadableInstanceStateIsAnError() {
        assertThatThrownBy(() -> WorkflowService.variablesFromInstanceState(
                mapper,
                "root.platform.workflows.tool",
                "{not-json"
        ))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("Workflow instance state is not readable at root.platform.workflows.tool");
    }

    @Test
    void emptyObjectHasNoToolVariables() throws Exception {
        assertThat(WorkflowService.variablesFromInstanceState(mapper, "root.wf", "{}")).isEmpty();
        assertThat(WorkflowService.variablesFromInstanceState(mapper, "root.wf", "  ")).isEmpty();
        assertThat(WorkflowService.variablesFromInstanceState(mapper, "root.wf", null)).isEmpty();
    }

    @Test
    void readsVariablesFromInstanceState() throws Exception {
        String state = """
                {"status":"COMPLETED","variables":{"severity":"high","reason":null}}
                """;
        assertThat(WorkflowService.variablesFromInstanceState(mapper, "root.wf", state))
                .containsEntry("severity", "high")
                .containsEntry("reason", "");
    }
}
