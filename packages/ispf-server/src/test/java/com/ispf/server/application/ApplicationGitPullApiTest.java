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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationGitPullApiTest {

    private static final String APP_ID = "git-pull-demo";
    private static final String TOKEN = "ghp_test_token_for_pull";
    private static final String BUNDLE_JSON = """
            {
              "version": "1.0.0",
              "displayName": "Git Pull Demo",
              "tablePrefix": "gitpull_",
              "schemaName": "app_git_pull_demo",
              "functions": [
                {
                  "objectPath": "root.platform.applications.git-pull-demo",
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
            """;

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
    void pullsReleaseAndRecordsProvenance() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appId": "%s",
                                  "displayName": "Git Pull Demo",
                                  "tablePrefix": "gitpull_",
                                  "schemaName": "app_git_pull_demo"
                                }
                                """.formatted(APP_ID)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/applications/" + APP_ID + "/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "github",
                                  "remoteUrl": "https://github.com/example/git-pull-demo.git",
                                  "defaultBranch": "main",
                                  "bundlePath": "bundle.json",
                                  "token": "%s"
                                }
                                """.formatted(TOKEN)))
                .andExpect(status().isOk());

        when(remoteClient.fetchBundle(
                eq("github"),
                eq("https://github.com/example/git-pull-demo.git"),
                eq("bundle.json"),
                eq("main"),
                anyString()
        )).thenReturn(new ApplicationGitRemoteClient.FetchedBundle(
                BUNDLE_JSON,
                "abcdef0123456789abcdef0123456789abcdef01",
                "main"
        ));

        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/git/pull")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OK")))
                .andExpect(jsonPath("$.version", is("1.0.0")))
                .andExpect(jsonPath("$.git.sha", is("abcdef0123456789abcdef0123456789abcdef01")))
                .andExpect(jsonPath("$.git.ref", is("main")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/applications/" + APP_ID + "/deploy/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source", is("git")))
                .andExpect(jsonPath("$[0].gitSha", is("abcdef0123456789abcdef0123456789abcdef01")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/applications/" + APP_ID + "/git"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastSyncedSha", is("abcdef0123456789abcdef0123456789abcdef01")))
                .andExpect(jsonPath("$.lastSyncedVersion", is("1.0.0")));
    }
}
