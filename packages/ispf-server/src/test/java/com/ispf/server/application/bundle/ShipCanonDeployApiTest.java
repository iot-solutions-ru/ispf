package com.ispf.server.application.bundle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0060: {@code POST .../deploy?strict=true} refuses LOGIC_HOST_DEVICE; default deploy does not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShipCanonDeployApiTest {

    private static final String BAD_DEVICE_HOST = """
            {
              "version":"1.0.0",
              "displayName":"Bad Hub",
              "tablePrefix":"b_",
              "schemaName":"app_ship_canon_bad",
              "objects":[{"parentPath":"root.platform.devices","name":"bad-hub","type":"DEVICE"}],
              "functions":[{
                "objectPath":"root.platform.devices.bad-hub",
                "functionName":"listItems",
                "source":{"type":"script","body":"{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\"}}]}"}
              }]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void defaultDeployAllowsLogicHostDeviceWarning() throws Exception {
        mockMvc.perform(post("/api/v1/applications/ship-canon-soft/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_DEVICE_HOST))
                .andExpect(status().isOk())
                // Authoring deploy applies; PARTIAL is OK if tree sync is incomplete — must not be ship-canon 400.
                .andExpect(jsonPath("$.status").value(anyOf(is("OK"), is("PARTIAL"))))
                .andExpect(jsonPath("$.status").value(not("ERROR")));
    }

    @Test
    void strictDeployRefusesLogicHostDevice() throws Exception {
        mockMvc.perform(post("/api/v1/applications/ship-canon-hard/deploy")
                        .param("strict", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_DEVICE_HOST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.shipCanon").value(true))
                .andExpect(jsonPath("$.deployed").value(false))
                .andExpect(jsonPath("$.issues[*].code", hasItem("LOGIC_HOST_DEVICE")));
    }
}
