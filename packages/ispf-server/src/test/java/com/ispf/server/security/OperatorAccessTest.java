package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class OperatorAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "operator")
    void operatorCanReadWorkQueue() throws Exception {
        mockMvc.perform(get("/api/v1/work-queue"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "operator")
    void operatorCannotCreateAlertRules() throws Exception {
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "test",
                                  "objectPath": "root",
                                  "watchVariable": "x",
                                  "conditionExpr": "true",
                                  "eventName": "test",
                                  "enabled": true,
                                  "edgeTrigger": false
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "admin")
    void adminCanCreateAlertRules() throws Exception {
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "rbac-test-rule",
                                  "objectPath": "root.platform.devices.demo-sensor-01",
                                  "watchVariable": "temperature",
                                  "conditionExpr": "true",
                                  "eventName": "thresholdExceeded",
                                  "enabled": false,
                                  "edgeTrigger": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("rbac-test-rule"));
    }

    @Test
    void localRoleHeaderGrantsOperatorAccess() throws Exception {
        mockMvc.perform(get("/api/v1/work-queue").header("X-ISPF-Role", "operator"))
                .andExpect(status().isOk());
    }

    @Test
    void localRoleHeaderBlocksOperatorFromCorrelatorsWrite() throws Exception {
        mockMvc.perform(post("/api/v1/correlators")
                        .header("X-ISPF-Role", "operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "blocked",
                                  "eventName": "x",
                                  "windowSeconds": 60,
                                  "minOccurrences": 1,
                                  "cooldownSeconds": 0,
                                  "actionType": "RUN_WORKFLOW",
                                  "actionTarget": "root.platform.workflows.demo-alarm-handler",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
