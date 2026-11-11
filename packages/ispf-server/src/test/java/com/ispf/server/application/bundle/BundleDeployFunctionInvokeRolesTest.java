package com.ispf.server.application.bundle;

import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BundleDeployFunctionInvokeRolesTest {

    private static final String HUB_PATH = "root.platform.singleton-blueprints.roles-singleton-hub";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    private static String bundleJson(String version, String exprOut, String scriptOut) {
        return """
                {
                  "version": "%s",
                  "displayName": "Roles Hub",
                  "tablePrefix": "rh_",
                  "schemaName": "app_roles_hub_test",
                  "blueprints": [{
                    "name": "roles-singleton-hub",
                    "type": "SINGLETON",
                    "variables": [],
                    "events": [],
                    "functions": [{
                      "name": "bpFn",
                      "description": "Blueprint expression function",
                      "inputSchema": {"name": "bp_in", "fields": [{"name": "unused", "type": "STRING"}]},
                      "outputSchema": {"name": "bp_out", "fields": [{"name": "value", "type": "STRING"}]},
                      "sourceType": "expression",
                      "sourceBody": "'%s'",
                      "version": "1",
                      "invokeRoles": []
                    }],
                    "bindings": [],
                    "parameters": {}
                  }],
                  "objects": [{
                    "parentPath": "root.platform.singleton-blueprints",
                    "name": "roles-singleton-hub",
                    "type": "CUSTOM"
                  }],
                  "functions": [{
                    "objectPath": "root.platform.singleton-blueprints.roles-singleton-hub",
                    "functionName": "scriptFn",
                    "version": "%s",
                    "descriptor": {
                      "inputSchema": {"name": "sc_in", "fields": [{"name": "unused", "type": "STRING"}]},
                      "outputSchema": {"name": "sc_out", "fields": [{"name": "error_code", "type": "STRING"}]}
                    },
                    "source": {
                      "type": "script",
                      "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"%s\\"}}]}"
                    }
                  }]
                }
                """.formatted(version, exprOut, version, scriptOut);
    }

    @Test
    void redeployPreservesInvokeRolesOnSingletonBlueprintAndBundleFunctions() throws Exception {
        mockMvc.perform(post("/api/v1/applications/roles-hub-app/deploy")
                        .param("runTests", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundleJson("1.0.0", "bp-v1", "OK_V1")))
                .andExpect(status().isOk());

        FunctionDescriptor existingBpFn = objectManager.require(HUB_PATH).functions().get("bpFn");
        assertThat(existingBpFn).isNotNull();
        objectManager.upsertFunction(HUB_PATH, new FunctionDescriptor(
                existingBpFn.name(),
                existingBpFn.description(),
                existingBpFn.inputSchema(),
                existingBpFn.outputSchema(),
                existingBpFn.sourceType(),
                existingBpFn.sourceBody(),
                existingBpFn.dataSourcePath(),
                existingBpFn.version(),
                List.of("admin")
        ));

        FunctionDescriptor existingScriptFn = objectManager.require(HUB_PATH).functions().get("scriptFn");
        assertThat(existingScriptFn).isNotNull();
        objectManager.upsertFunction(HUB_PATH, new FunctionDescriptor(
                existingScriptFn.name(),
                existingScriptFn.description(),
                existingScriptFn.inputSchema(),
                existingScriptFn.outputSchema(),
                existingScriptFn.sourceType(),
                existingScriptFn.sourceBody(),
                existingScriptFn.dataSourcePath(),
                existingScriptFn.version(),
                List.of("admin", "auditor")
        ));

        mockMvc.perform(post("/api/v1/applications/roles-hub-app/deploy")
                        .param("runTests", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundleJson("2.0.0", "bp-v2", "OK_V2")))
                .andExpect(status().isOk());

        FunctionDescriptor redeployedBpFn = objectManager.require(HUB_PATH).functions().get("bpFn");
        assertThat(redeployedBpFn).isNotNull();
        assertThat(redeployedBpFn.sourceBody()).isEqualTo("'bp-v2'");
        assertThat(redeployedBpFn.invokeRoles()).containsExactly("admin");

        FunctionDescriptor redeployedScriptFn = objectManager.require(HUB_PATH).functions().get("scriptFn");
        assertThat(redeployedScriptFn).isNotNull();
        assertThat(redeployedScriptFn.sourceBody()).contains("OK_V2");
        assertThat(redeployedScriptFn.invokeRoles()).containsExactly("admin", "auditor");

        objectManager.reloadPathFromDatabase(HUB_PATH);
        assertThat(objectManager.require(HUB_PATH).functions().get("bpFn").invokeRoles())
                .containsExactly("admin");
        assertThat(objectManager.require(HUB_PATH).functions().get("scriptFn").invokeRoles())
                .containsExactly("admin", "auditor");
    }
}
