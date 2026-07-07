package com.ispf.server.platform;

import com.ispf.server.bootstrap.HaystackBlueprintBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrickInferApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void infersFromLabDeviceObjectPath() throws Exception {
        mockMvc.perform(get("/api/v1/platform/brick/infer")
                        .param("objectPath", HaystackBlueprintBootstrap.DEMO_DEVICE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectPath").value(HaystackBlueprintBootstrap.DEMO_DEVICE_PATH))
                .andExpect(jsonPath("$.entityKind").value("equip"))
                .andExpect(jsonPath("$.confidence").exists())
                .andExpect(jsonPath("$.reason").exists())
                .andExpect(jsonPath("$.brickClassCompact").exists())
                .andExpect(jsonPath("$.pointMappingTags").isArray());
    }

    @Test
    void infersTemperatureSensorFromTags() throws Exception {
        mockMvc.perform(get("/api/v1/platform/brick/infer")
                        .param("tags", "point,sensor,temp")
                        .param("haystackKind", "point"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brickClassCompact").value("brick:Temperature_Sensor"))
                .andExpect(jsonPath("$.confidence").value("high"))
                .andExpect(jsonPath("$.entityKind").value("point"));
    }

    @Test
    void infersAirHandlerFromTags() throws Exception {
        mockMvc.perform(get("/api/v1/platform/brick/infer")
                        .param("tags", "equip,ahu,air")
                        .param("haystackKind", "equip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brickClassCompact").value("brick:Air_Handler_Unit"))
                .andExpect(jsonPath("$.confidence").value("high"));
    }

    @Test
    void rejectsMissingObjectPathAndTags() throws Exception {
        mockMvc.perform(get("/api/v1/platform/brick/infer"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBothObjectPathAndTags() throws Exception {
        mockMvc.perform(get("/api/v1/platform/brick/infer")
                        .param("objectPath", HaystackBlueprintBootstrap.DEMO_DEVICE_PATH)
                        .param("tags", "equip,meter"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownObjectPath() throws Exception {
        mockMvc.perform(get("/api/v1/platform/brick/infer")
                        .param("objectPath", "root.platform.devices.does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
