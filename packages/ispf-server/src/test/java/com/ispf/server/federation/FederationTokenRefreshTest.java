package com.ispf.server.federation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.secrets-key=test-secret-key-for-federation-phase7"
})
class FederationTokenRefreshTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FederationPeerStore peerStore;

    @Autowired
    private FederationPeerAuthService authService;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @Test
    void serviceAccountPeerRefreshesToken() throws Exception {
        String token = loginAdmin();
        String baseUrl = "http://127.0.0.1:" + port;

        MvcResult created = mockMvc.perform(post("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "auth-refresh-peer",
                                  "baseUrl": "%s",
                                  "pathPrefix": "root.platform",
                                  "authMode": "SERVICE_ACCOUNT",
                                  "authUsername": "admin",
                                  "authPassword": "admin"
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authMode").value("SERVICE_ACCOUNT"))
                .andExpect(jsonPath("$.hasAuthToken").value(true))
                .andReturn();

        String id = extractJsonField(created.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/v1/federation/peers/" + id + "/auth-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStatus").value("OK"))
                .andExpect(jsonPath("$.serviceAccountConfigured").value(true))
                .andExpect(jsonPath("$.tokenExpiresAt").exists());

        mockMvc.perform(post("/api/v1/federation/peers/" + id + "/refresh-token")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authStatus").value("OK"));

        FederationPeer peer = peerStore.findById(java.util.UUID.fromString(id)).orElseThrow();
        assertThat(peer.authToken()).isNotBlank();
        assertThat(peer.tokenExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void shouldRefreshWhenTokenNearExpiry() {
        Instant now = Instant.now();
        FederationPeer expiring = samplePeer(now.plus(30, ChronoUnit.MINUTES));
        FederationPeer fresh = samplePeer(now.plus(12, ChronoUnit.HOURS));
        FederationPeer expired = samplePeer(now.minus(1, ChronoUnit.MINUTES));

        assertThat(FederationPeerAuthService.shouldRefresh(expiring, now)).isTrue();
        assertThat(FederationPeerAuthService.shouldRefresh(expired, now)).isTrue();
        assertThat(FederationPeerAuthService.shouldRefresh(fresh, now)).isFalse();
    }

    @Test
    void refreshNowRestoresExpiredServiceAccountPeer() throws Exception {
        String token = loginAdmin();
        String baseUrl = "http://127.0.0.1:" + port;

        MvcResult created = mockMvc.perform(post("/api/v1/federation/peers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "expired-auth-peer",
                                  "baseUrl": "%s",
                                  "pathPrefix": "root.platform",
                                  "authMode": "SERVICE_ACCOUNT",
                                  "authUsername": "admin",
                                  "authPassword": "admin"
                                }
                                """.formatted(baseUrl)))
                .andExpect(status().isOk())
                .andReturn();

        java.util.UUID peerId = java.util.UUID.fromString(
                extractJsonField(created.getResponse().getContentAsString(), "id")
        );
        peerStore.updateAuthState(
                peerId,
                "stale-token",
                Instant.now().minus(1, ChronoUnit.HOURS),
                FederationAuthStatus.EXPIRING,
                Instant.now().minus(2, ChronoUnit.HOURS),
                null
        );

        authService.refreshNow(peerId);

        FederationPeer peer = peerStore.findById(peerId).orElseThrow();
        assertThat(peer.authStatus()).isEqualTo(FederationAuthStatus.OK);
        assertThat(peer.authToken()).isNotEqualTo("stale-token");
        assertThat(peer.tokenExpiresAt()).isAfter(Instant.now());
    }

    private FederationPeer samplePeer(Instant tokenExpiresAt) {
        return new FederationPeer(
                java.util.UUID.randomUUID(),
                "sample",
                "http://127.0.0.1:8080",
                "token",
                "root.platform",
                true,
                null,
                FederationConnectionMode.HTTP_PULL,
                FederationAuthMode.SERVICE_ACCOUNT,
                tokenExpiresAt,
                "admin",
                "enc",
                FederationAuthStatus.OK,
                Instant.now(),
                null,
                Instant.now(),
                Instant.now()
        );
    }

    private String loginAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "admin", "password": "admin" }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(login.getResponse().getContentAsString(), "token");
    }

    private static String extractJsonField(String json, String field) {
        return json.replaceAll("(?s).*\\\"" + field + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\".*", "$1");
    }
}
