package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /deploy always rejects a structurally invalid manifest and runs manifest tests unless opted out.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BundleDeployDefaultsApiTest {

    private static final String MISSING_VERSION = """
            {
              "displayName":"Broken",
              "tablePrefix":"b_",
              "schemaName":"app_bnd1_bad",
              "objects":[{"parentPath":"root.platform.devices","name":"x","type":"DEVICE"}]
            }
            """;

    private static final String WITH_TEST = """
            {
              "version":"1.0.0",
              "displayName":"BND1",
              "tablePrefix":"b1_",
              "schemaName":"app_bnd1_tests",
              "blueprints":[{
                "name":"bnd1-hub",
                "type":"SINGLETON",
                "variables":[],
                "events":[],
                "functions":[],
                "bindings":[],
                "parameters":{}
              }],
              "objects":[{
                "parentPath":"root.platform.singleton-blueprints",
                "name":"bnd1-hub",
                "type":"CUSTOM"
              }],
              "functions":[{
                "objectPath":"root.platform.singleton-blueprints.bnd1-hub",
                "functionName":"ping",
                "descriptor":{
                  "inputSchema":{"name":"ping_in","fields":[{"name":"unused","type":"STRING"}]},
                  "outputSchema":{"name":"ping_out","fields":[{"name":"error_code","type":"STRING"}]}
                },
                "source":{"type":"script","body":"{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\"}}]}"}
              }],
              "tests":[{
                "id":"ping-ok",
                "kind":"function",
                "objectPath":"root.platform.singleton-blueprints.bnd1-hub",
                "functionName":"ping",
                "input":{"unused":"x"},
                "expect":{"errorCode":"OK"}
              }]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void structuralErrorBlocksDeploy() throws Exception {
        mockMvc.perform(post("/api/v1/applications/bnd1-broken/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MISSING_VERSION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.deployed").value(false))
                .andExpect(jsonPath("$.shipCanon").value(false));
    }

    @Test
    void deployRunsManifestTestsByDefault() throws Exception {
        mockMvc.perform(post("/api/v1/applications/bnd1-tests/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WITH_TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testResults[0].id").value("ping-ok"))
                .andExpect(jsonPath("$.testResults[0].status").value("PASS"));
    }

    @Test
    void runTestsFalseSkipsManifestTests() throws Exception {
        mockMvc.perform(post("/api/v1/applications/bnd1-skip-tests/deploy")
                        .param("runTests", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WITH_TEST.replace("app_bnd1_tests", "app_bnd1_skip")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testResults").doesNotExist());
    }
}
