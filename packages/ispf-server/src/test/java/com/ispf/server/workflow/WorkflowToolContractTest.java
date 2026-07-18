package com.ispf.server.workflow;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowToolContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validateInputRequiresFields() throws Exception {
        String schema = "{\"required\":[\"alarmId\"]}";
        assertThatThrownBy(() -> WorkflowToolContract.validateInput(mapper, schema, Map.of()))
                .isInstanceOf(WorkflowToolContract.WorkflowToolContractException.class);
        WorkflowToolContract.validateInput(mapper, schema, Map.of("alarmId", "a-1"));
    }

    @Test
    void extractOutputProjectsProperties() throws Exception {
        String schema = "{\"properties\":{\"severity\":{},\"reason\":{}}}";
        Map<String, String> out = WorkflowToolContract.extractOutput(
                mapper,
                schema,
                Map.of("severity", "high", "reason", "spike", "noise", "x")
        );
        assertThat(out).containsEntry("severity", "high").containsEntry("reason", "spike").doesNotContainKey("noise");
    }
}
