package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "ispf.security.rbac-enabled=true")
class PlatformBackupAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "operator")
    void operatorCannotExportPlatformBackup() throws Exception {
        mockMvc.perform(get("/api/v1/platform/backup/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "admin")
    void adminCanExportPlatformBackup() throws Exception {
        mockMvc.perform(get("/api/v1/platform/backup/export"))
                .andExpect(status().isOk());
    }
}
