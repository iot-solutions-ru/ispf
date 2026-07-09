package com.ispf.server.platform;

import com.ispf.server.config.FunctionProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformRuntimeApiTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FunctionProperties functionProperties;

    private boolean auditEnabledBefore;
    private boolean auditAsyncBefore;

    @BeforeEach
    void enableSyncFunctionAudit() {
        FunctionProperties.Audit audit = functionProperties.getAudit();
        auditEnabledBefore = audit.isEnabled();
        auditAsyncBefore = audit.isAsyncEnabled();
        audit.setEnabled(true);
        audit.setAsyncEnabled(false);
    }

    @AfterEach
    void restoreFunctionAudit() {
        FunctionProperties.Audit audit = functionProperties.getAudit();
        audit.setEnabled(auditEnabledBefore);
        audit.setAsyncEnabled(auditAsyncBefore);
    }

    @Test
    void listsFunctionInvocationAudit() throws Exception {
        mockMvc.perform(patch("/api/v1/objects/by-path")
                        .param("path", DEVICE)
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"functionAuditEnabled": true}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", DEVICE)
                        .param("name", "acknowledgeAlarm")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/platform/function-invocations")
                        .param("objectPath", DEVICE)
                        .param("functionName", "acknowledgeAlarm")
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].objectPath").value(DEVICE))
                .andExpect(jsonPath("$[0].functionName").value("acknowledgeAlarm"))
                .andExpect(jsonPath("$[0].success").value(true))
                .andExpect(jsonPath("$[0].outputJson").exists());
    }
}
