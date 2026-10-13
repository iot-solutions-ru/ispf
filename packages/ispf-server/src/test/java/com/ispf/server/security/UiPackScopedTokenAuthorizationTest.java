package com.ispf.server.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
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
        "ispf.security.local-role-header-enabled=false",
        "ispf.ui-pack.packs-dir=build/ui-pack-wave2a-test"
})
class UiPackScopedTokenAuthorizationTest {

    private static final String CREATE_USER = """
            {"username":"wave2a-must-not-exist","password":"wave2a-must-not-exist","displayName":"x","roles":["operator"]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void installPacks() throws Exception {
        writePack("partner-app", "partner");
        writePack("site-app", "firstParty");
    }

    @Test
    void packScopedAdminTokenCannotCallAdminMutate() throws Exception {
        String admin = login("http://localhost/login");
        mockMvc.perform(get("/api/v1/security/users").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/security/users")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        MvcResult minted = mockMvc.perform(post("/api/v1/security/ui-pack-tokens")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","appId":"partner-app"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uiPackAppId").value("partner-app"))
                .andExpect(jsonPath("$.purpose").value("ui-pack-bff"))
                .andReturn();
        String scoped = tokenOf(minted);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + scoped))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uiPackAppId").value("partner-app"))
                .andExpect(jsonPath("$.roles", hasItem("admin")))
                .andExpect(jsonPath("$.roles", hasItem("UI_PACK")));

        mockMvc.perform(get("/api/v1/security/users").header("Authorization", "Bearer " + scoped))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/security/users")
                        .header("Authorization", "Bearer " + scoped)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_USER))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/security/users")
                        .header("Authorization", "Bearer " + scoped)
                        .header("X-ISPF-On-Behalf-Of-User", "operator")
                        .header("X-ISPF-On-Behalf-Of-Roles", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void packScopedTokenCanInvokeBffForItsAppOnly() throws Exception {
        String admin = login(null);
        MvcResult minted = mockMvc.perform(post("/api/v1/security/ui-pack-tokens")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","appId":"partner-app"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String scoped = tokenOf(minted);
        String invoke = """
                {"objectPath":"root.platform","functionName":"no-such-wave2a"}
                """;

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .header("Authorization", "Bearer " + scoped)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .header("Authorization", "Bearer " + scoped)
                        .header("Referer", "http://localhost/apps/partner-app/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/bff/invoke")
                        .header("Authorization", "Bearer " + scoped)
                        .header("Referer", "http://localhost/apps/other-app/index.html")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoke))
                .andExpect(status().isForbidden());
    }

    @Test
    void partnerPackRefererCannotMintConsoleLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Referer", "http://localhost/apps/partner-app/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Partner")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Referer", "http://localhost/apps/site-app/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.roles", hasItem("admin")));
    }

    @Test
    void partnerPackContextCookieWithoutRefererCannotMintConsoleLogin() throws Exception {
        mockMvc.perform(get("/apps/partner-app/")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String setCookie = String.join(",", result.getResponse().getHeaders("Set-Cookie"));
                    org.junit.jupiter.api.Assertions.assertTrue(
                            setCookie.contains("ISPF_UI_PACK_CTX=partner-app"),
                            "expected partner context cookie, got: " + setCookie
                    );
                });

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new jakarta.servlet.http.Cookie("ISPF_UI_PACK_CTX", "partner-app"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Partner")));
    }

    @Test
    void partnerPackAppHeaderWithoutRefererCannotMintConsoleLogin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-ISPF-Ui-Pack-App", "partner-app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Partner")));
    }

    private String login(String referer) throws Exception {
        var request = post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"admin","password":"admin"}
                        """);
        if (referer != null) {
            request.header("Referer", referer);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return tokenOf(result);
    }

    private static String tokenOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private static void writePack(String appId, String trust) throws Exception {
        Path dir = Path.of("build", "ui-pack-wave2a-test", appId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("ui-pack.json"), """
                {"appId":"%s","entry":"index.html","uiPackTrust":"%s"}
                """.formatted(appId, trust));
        Files.writeString(dir.resolve("index.html"), "<html>" + appId + "</html>");
    }
}
