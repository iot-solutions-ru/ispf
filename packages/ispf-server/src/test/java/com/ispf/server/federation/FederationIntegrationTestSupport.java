package com.ispf.server.federation;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared flake budget for federation integration tests (S27).
 * See docs/FEDERATION.md § Integration test flake budget.
 */
final class FederationIntegrationTestSupport {

    /** Outbound tunnel WebSocket connect + linked peer registration. */
    static final long TUNNEL_CONNECT_TIMEOUT_SECONDS = System.getenv("CI") != null ? 120 : 60;

    /** Federation proxy first successful read after tunnel connect. */
    static final long PROXY_READY_TIMEOUT_SECONDS = System.getenv("CI") != null ? 60 : 30;

    /** Store-forward buffer drain after tunnel reconnect. */
    static final long BUFFER_DRAIN_TIMEOUT_SECONDS = System.getenv("CI") != null ? 120 : 90;

    static final long CONNECT_RETRY_INTERVAL_MS = 5_000;

    private FederationIntegrationTestSupport() {
    }

    static String loginAdmin(MockMvc mockMvc) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(login.getResponse().getContentAsString(), "token");
    }

    static String extractJsonField(String json, String field) {
        return json.replaceAll("(?s).*\\\"" + field + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*", "$1");
    }
}
