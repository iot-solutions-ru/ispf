package com.ispf.server.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationGitApiTest {

    private static final String TOKEN = "ghp_test_token_for_unit";
    private static final String CRED_PATH = "root.platform.applications.git-demo.git-credential";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void connectsAndReportsStatus() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "git-demo",
                                  "displayName": "Git Demo",
                                  "tablePrefix": "gitdemo_",
                                  "schemaName": "app_git_demo"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/applications/git-demo/git"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(false)));

        mockMvc.perform(put("/api/v1/applications/git-demo/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "github",
                                  "remoteUrl": "https://github.com/example/git-demo.git",
                                  "defaultBranch": "main",
                                  "bundlePath": "bundle.json",
                                  "token": "%s",
                                  "credentialObjectPath": "root.evil.steal"
                                }
                                """.formatted(TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(true)))
                .andExpect(jsonPath("$.provider", is("github")))
                .andExpect(jsonPath("$.remoteUrl", is("https://github.com/example/git-demo.git")))
                .andExpect(jsonPath("$.credentialObjectPath", is(CRED_PATH)))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(content().string(not(containsString(TOKEN))));

        mockMvc.perform(get("/api/v1/applications/git-demo/git/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(true)))
                .andExpect(jsonPath("$.credentialConfigured", is(true)))
                .andExpect(content().string(not(containsString(TOKEN))));

        mockMvc.perform(get("/api/v1/credentials/by-path")
                        .param("path", CRED_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSecret", is(true)));

        mockMvc.perform(delete("/api/v1/applications/git-demo/git"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured", is(false)));

        mockMvc.perform(get("/api/v1/credentials/by-path")
                        .param("path", CRED_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NOT_FOUND")));
    }

    @Test
    void rejectsRemoteUrlWithEmbeddedCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "git-bad-url",
                                  "displayName": "Git Bad Url",
                                  "tablePrefix": "gitbad_",
                                  "schemaName": "app_git_bad"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/applications/git-bad-url/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "github",
                                  "remoteUrl": "https://user:secret@github.com/example/repo.git",
                                  "token": "%s"
                                }
                                """.formatted(TOKEN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresTokenWhenRemoteHostChanges() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "git-host-change",
                                  "displayName": "Git Host Change",
                                  "tablePrefix": "githc_",
                                  "schemaName": "app_git_host_change"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/applications/git-host-change/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "github",
                                  "remoteUrl": "https://github.com/example/git-host-change.git",
                                  "token": "%s"
                                }
                                """.formatted(TOKEN)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/applications/git-host-change/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "github",
                                  "remoteUrl": "https://gitlab.com/example/git-host-change.git"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uninstallClearsGitCredential() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "git-uninstall",
                                  "displayName": "Git Uninstall",
                                  "tablePrefix": "gitun_",
                                  "schemaName": "app_git_uninstall"
                                }
                                """))
                .andExpect(status().isOk());

        String credPath = "root.platform.applications.git-uninstall.git-credential";
        mockMvc.perform(put("/api/v1/applications/git-uninstall/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "github",
                                  "remoteUrl": "https://github.com/example/git-uninstall.git",
                                  "token": "%s"
                                }
                                """.formatted(TOKEN)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/solutions/installed/applications/git-uninstall"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/credentials/by-path")
                        .param("path", credPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NOT_FOUND")));
    }
}
