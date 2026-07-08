package com.ispf.server.security.mfa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true",
        "ispf.security.mfa.enabled=true",
        "ispf.security.mfa.required-for-admin=true"
})
class MfaAdminLoginApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MfaService mfaService;

    @Test
    void adminLoginRequiresTotpWhenMfaRequiredForAdmin() throws Exception {
        MfaService.EnrollmentStart enrollment = mfaService.startEnrollment("admin");
        String enrollCode = TotpUtil.generateCode(enrollment.secret(), Instant.now());
        mfaService.verifyEnrollment("admin", enrollCode);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isUnauthorized());

        String loginCode = TotpUtil.generateCode(enrollment.secret(), Instant.now());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "admin",
                                  "totpCode": "%s"
                                }
                                """.formatted(loginCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("admin"));
    }
}
