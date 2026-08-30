package com.ispf.server.api;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.DemoFixtureBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.query.ObjectQueryFunctionSupport;
import com.ispf.server.query.oq.ObjectQuerySpec;
import com.ispf.server.query.oq.ObjectQuerySpecParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class FunctionInvokeApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectTemplateService objectTemplateService;

    @Autowired
    private ObjectMapper objectMapper;

    private String aclTargetPath;
    private String aclQueryPath;

    @BeforeEach
    void ensureDemoSensorAcknowledgeAlarmFunction() {
        var node = objectManager.tree().findByPath(DEMO_DEVICE);
        if (node.isEmpty() || !node.get().functions().containsKey("acknowledgeAlarm")) {
            objectTemplateService.applyTemplate(DEMO_DEVICE, DemoFixtureBootstrap.MQTT_SENSOR_MODEL);
        }
    }

    @AfterEach
    void cleanupAclFixture() {
        deleteIfPresent(aclQueryPath);
        deleteIfPresent(aclTargetPath);
        aclQueryPath = null;
        aclTargetPath = null;
    }

    @Test
    void invokesFunctionWithoutPayloadBody() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", DEMO_DEVICE)
                        .param("name", "acknowledgeAlarm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].success").value(true));
    }

    @Test
    void invokesFunctionWithPartialPayloadRowsOnly() throws Exception {
        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", DEMO_DEVICE)
                        .param("name", "acknowledgeAlarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[{}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].success").value(true));
    }

    @Test
    @WithMockUser(roles = "operator")
    void objectQueryFunctionDoesNotReturnRestrictedVariable() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String targetName = "function-oq-acl-target-" + suffix;
        aclTargetPath = "root.platform.devices." + targetName;
        objectManager.create(
                "root.platform.devices",
                targetName,
                ObjectType.DEVICE,
                "Function OQ ACL target",
                "",
                null
        );
        DataSchema secretSchema = DataSchema.builder("secret")
                .field("value", FieldType.STRING)
                .build();
        objectManager.createVariable(
                aclTargetPath,
                "secret",
                secretSchema,
                true,
                true,
                DataRecord.single(secretSchema, Map.of("value", "restricted-function-value")),
                true,
                null,
                List.of("engineer"),
                List.of()
        );

        String queryName = "function-oq-acl-query-" + suffix;
        aclQueryPath = "root.platform.queries." + queryName;
        objectManager.create(
                "root.platform.queries",
                queryName,
                ObjectType.CUSTOM,
                "Function OQ ACL query",
                "",
                null
        );
        ObjectQuerySpecParser parser = new ObjectQuerySpecParser(objectMapper);
        ObjectQuerySpec spec = parser.parse("""
                {
                  "from": {
                    "sourcePathPattern": "%s",
                    "objectTypes": ["DEVICE"]
                  },
                  "fields": [
                    {"name": "path", "source": "path", "alias": "row"},
                    {"name": "secretValue", "ref": "{row}/secret/value"},
                    {
                      "name": "secretHistory",
                      "ref": "{row}/secret",
                      "historian": {"fn": "latest", "window": "15m"}
                    }
                  ]
                }
                """.formatted(aclTargetPath));
        objectManager.upsertFunction(
                aclQueryPath,
                ObjectQueryFunctionSupport.runFunction(parser.writeSpec(spec))
        );

        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", aclQueryPath)
                        .param("name", "run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].rowCount").value(1))
                .andExpect(jsonPath("$.rows[0].rows", not(containsString("secretValue"))))
                .andExpect(jsonPath("$.rows[0].rows", not(containsString("secretHistory"))))
                .andExpect(jsonPath("$.rows[0].rows", not(containsString("restricted-function-value"))));
    }

    private void deleteIfPresent(String path) {
        if (path != null && objectManager.tree().findByPath(path).isPresent()) {
            objectManager.delete(path);
        }
    }
}
