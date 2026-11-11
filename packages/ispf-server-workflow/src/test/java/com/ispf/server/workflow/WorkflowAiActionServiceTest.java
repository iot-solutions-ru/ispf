package com.ispf.server.workflow;

import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.config.AiProperties;
import com.ispf.server.spi.CredentialLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowAiActionServiceTest {

    private WorkflowAiActionService service;

    @BeforeEach
    void setUp() {
        AiProperties ai = new AiProperties();
        ai.setProvider("noop");
        CredentialLookup credentials = new CredentialLookup() {
            @Override
            public Optional<String> resolveSecret(String objectPath) {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> describe(String objectPath) {
                return Map.of();
            }
        };
        service = new WorkflowAiActionService(ai, credentials, new ObjectMapper());
    }

    @Test
    void invokeAgentPromptOnlyUsesLlmComplete() throws WorkflowException {
        String out = service.invokeAgent("Summarize pump trend", "ask", "", 1);
        assertTrue(out.contains("noop"));
    }

    @Test
    void invokeAgentRejectsNonEmptyToolAllowlist() {
        WorkflowException ex = assertThrows(
                WorkflowException.class,
                () -> service.invokeAgent("goal", "ask", "get_variable_history", 1)
        );
        assertTrue(ex.getMessage().contains("toolAllowlist"));
    }

    @Test
    void invokeAgentRejectsMaxStepsAboveOne() {
        WorkflowException ex = assertThrows(
                WorkflowException.class,
                () -> service.invokeAgent("goal", "ask", "", 8)
        );
        assertTrue(ex.getMessage().contains("maxSteps"));
    }
}
