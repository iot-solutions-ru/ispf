package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowToolInvokeApiTest {

    private static final String WORKFLOW = "root.platform.workflows.tool-output-demo";

    private static final String BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="tool-output-demo" name="Tool Output Demo" isExecutable="true">
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow sourceRef="start" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private WorkflowService workflowService;

    @MockitoSpyBean
    private WorkflowInstanceStatePublisher statePublisher;

    @BeforeEach
    void ensureWorkflow() throws Exception {
        if (objectManager.tree().findByPath(WORKFLOW).isEmpty()) {
            objectManager.create(
                    "root.platform.workflows",
                    "tool-output-demo",
                    ObjectType.WORKFLOW,
                    "Tool Output Demo",
                    "",
                    "workflow-v1"
            );
            workflowService.ensureWorkflowStructure(WORKFLOW);
        }
        mockMvc.perform(put("/api/v1/workflows/by-path/bpmn")
                        .param("path", WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("bpmnXml", BPMN))))
                .andExpect(status().isOk());
        objectManager.setVariableValue(
                WORKFLOW,
                "status",
                DataRecord.single(
                        DataSchema.builder("status").field("value", FieldType.STRING).build(),
                        Map.of("value", WorkflowLifecycleStatus.ACTIVE.name())
                )
        );
        objectManager.persistNodeTree(WORKFLOW);
    }

    @Test
    void invokeProjectsInputIntoOutput() throws Exception {
        mockMvc.perform(post("/api/v1/workflows/by-path/invoke-tool")
                        .param("path", WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input":{"marker":"seen"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.output.marker").value("seen"));
    }

    @Test
    void invokeRejectsUnreadableInstanceState() throws Exception {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            String path = invocation.getArgument(0);
            objectManager.setVariableValue(
                    path,
                    "instanceState",
                    DataRecord.single(
                            DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                            Map.of("value", "{not-json")
                    )
            );
            return null;
        }).when(statePublisher).publish(anyString(), any(WorkflowInstance.class));
        try {
            mockMvc.perform(post("/api/v1/workflows/by-path/invoke-tool")
                            .param("path", WORKFLOW)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"input":{"marker":"seen"}}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(
                            "Workflow instance state is not readable at " + WORKFLOW
                    )));
        } finally {
            Mockito.reset(statePublisher);
        }
    }
}
