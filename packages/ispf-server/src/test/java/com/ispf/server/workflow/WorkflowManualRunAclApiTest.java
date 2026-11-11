package com.ispf.server.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class WorkflowManualRunAclApiTest {

    private static final String DEMO_WORKFLOW = "root.platform.workflows.demo-alarm-handler";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void operatorWithoutInvokeAclCannotRunWorkflow() throws Exception {
        String admin = login("admin", "admin");
        String operator = login("operator", "operator");

        mockMvc.perform(put("/api/v1/objects/by-path/acl")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", DEMO_WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entries": [
                                    { "principalType": "ROLE", "principalId": "admin", "permission": "OWNER" },
                                    { "principalType": "ROLE", "principalId": "operator", "permission": "VIEWER" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        try {
            mockMvc.perform(post("/api/v1/workflows/by-path/run")
                            .header("Authorization", "Bearer " + operator)
                            .param("path", DEMO_WORKFLOW))
                    .andExpect(status().isForbidden());
        } finally {
            mockMvc.perform(put("/api/v1/objects/by-path/acl")
                            .header("Authorization", "Bearer " + admin)
                            .param("path", DEMO_WORKFLOW)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "entries": [] }
                                    """))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void manualRunRejectsNonActiveWorkflow() throws Exception {
        String admin = login("admin", "admin");

        mockMvc.perform(put("/api/v1/workflows/by-path/status")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", DEMO_WORKFLOW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DRAFT"}
                                """))
                .andExpect(status().isOk());

        try {
            mockMvc.perform(post("/api/v1/workflows/by-path/run")
                            .header("Authorization", "Bearer " + admin)
                            .param("path", DEMO_WORKFLOW))
                    .andExpect(status().isBadRequest());
        } finally {
            mockMvc.perform(put("/api/v1/workflows/by-path/status")
                            .header("Authorization", "Bearer " + admin)
                            .param("path", DEMO_WORKFLOW)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"ACTIVE"}
                                    """))
                    .andExpect(status().isOk());
        }
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
