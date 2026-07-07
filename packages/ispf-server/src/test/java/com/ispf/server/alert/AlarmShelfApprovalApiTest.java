package com.ispf.server.alert;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true",
        "ispf.alarm-shelf.approval-required=true"
})
class AlarmShelfApprovalApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String EVENT_NAME = "thresholdExceeded";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlarmShelfApprovalService approvalService;

    @AfterEach
    void cleanup() {
        approvalService.listPending().forEach(request -> approvalService.removePending(request.id()));
    }

    @Test
    void shelveCreatesPendingRequestUntilApproved() throws Exception {
        String adminToken = login("admin", "admin");
        String createBody = """
                {
                  "objectPath": "%s",
                  "eventName": "%s",
                  "durationMinutes": 15,
                  "comment": "needs approval"
                }
                """.formatted(DEMO_DEVICE, EVENT_NAME);

        MvcResult pending = mockMvc.perform(post("/api/v1/alarm-shelves")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectPath").value(DEMO_DEVICE))
                .andExpect(jsonPath("$.requestedBy").value("admin"))
                .andReturn();

        String requestId = pending.getResponse().getContentAsString()
                .replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/alarm-shelves/requests")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(requestId)));

        mockMvc.perform(post("/api/v1/alarm-shelves/requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    private String login(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getContentAsString()
                .replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
