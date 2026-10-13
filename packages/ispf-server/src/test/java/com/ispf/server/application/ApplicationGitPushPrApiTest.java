package com.ispf.server.application;

import com.ispf.server.application.git.ApplicationGitRemoteClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationGitPushPrApiTest {

    private static final String APP_ID = "git-push-demo";
    private static final String TOKEN = "ghp_test_token_for_push";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationGitRemoteClient remoteClient;

    @TestConfiguration
    static class MockRemoteConfig {
        @Bean
        @Primary
        ApplicationGitRemoteClient applicationGitRemoteClient() {
            return org.mockito.Mockito.mock(ApplicationGitRemoteClient.class);
        }
    }

    @Test
    void pushesTreeAndReturnsPrUrl() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Git Push Demo",
                                  "tablePrefix": "gitpush_",
                                  "schemaName": "app_git_push_demo"
                                }
                                """.formatted(APP_ID)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "displayName": "Git Push Demo",
                                  "tablePrefix": "gitpush_",
                                  "schemaName": "app_git_push_demo",
                                  "functions": [
                                    {
                                      "objectPath": "root.platform.applications.git-push-demo",
                                      "functionName": "ping",
                                      "version": "1",
                                      "descriptor": {
                                        "inputSchema": { "name": "in", "fields": [] },
                                        "outputSchema": {
                                          "name": "out",
                                          "fields": [
                                            {"name": "error_code", "type": "STRING"},
                                            {"name": "error_message", "type": "STRING"}
                                          ]
                                        }
                                      },
                                      "source": {
                                        "type": "script",
                                        "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\",\\"error_message\\":\\"\\"}}]}"
                                      }
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/applications/" + APP_ID + "/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "github",
                                  "remoteUrl": "https://github.com/example/git-push-demo.git",
                                  "defaultBranch": "main",
                                  "bundlePath": "bundle.json",
                                  "token": "%s"
                                }
                                """.formatted(TOKEN)))
                .andExpect(status().isOk());

        when(remoteClient.pushBundleAndOpenPr(
                eq("github"),
                eq("https://github.com/example/git-push-demo.git"),
                eq("bundle.json"),
                eq("main"),
                anyString(),
                isNull(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(new ApplicationGitRemoteClient.PushPrResult(
                "ispf/app-git-push-demo-test",
                "main",
                "abc123",
                "https://github.com/example/git-push-demo/pull/42",
                42
        ));

        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/git/push-pr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OK")))
                .andExpect(jsonPath("$.prUrl", is("https://github.com/example/git-push-demo/pull/42")))
                .andExpect(jsonPath("$.prNumber", is(42)))
                .andExpect(jsonPath("$.baseBranch", is("main")))
                .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void rejectsBranchNameEqualToDefault() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "git-push-bad-branch",
                                  "displayName": "Git Push Bad",
                                  "tablePrefix": "gitpb_",
                                  "schemaName": "app_git_push_bad"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/applications/git-push-bad-branch/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "github",
                                  "remoteUrl": "https://github.com/example/git-push-bad.git",
                                  "defaultBranch": "main",
                                  "token": "%s"
                                }
                                """.formatted(TOKEN)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/applications/git-push-bad-branch/git/push-pr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "branchName": "main" }
                                """))
                .andExpect(status().isBadRequest());
    }
}
