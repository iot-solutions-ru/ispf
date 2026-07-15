package com.ispf.server.mes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VirtualSimulatorProfilesTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void restoreDemoDeviceDriver() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", DEMO_DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "virtual",
                                  "pollIntervalMs": 2000,
                                  "configuration": {
                                    "baseTemperature": "22.0",
                                    "amplitude": "15.0",
                                    "periodSec": "60"
                                  },
                                  "pointMappings": {"temperature": "sim"},
                                  "autoStart": false
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void deploySimulatorBundleAndRunMeterWeighbridgeProfiles() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String appId = "simulator-pf09-" + suffix;
        String meterDevice = "root.platform.devices.sim-meter-" + suffix;
        String bridgeDevice = "root.platform.devices.sim-bridge-" + suffix;

        String bundleTemplate = new ClassPathResource("simulator-profiles-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        String bundle = bundleTemplate
                .replace("sim-meter-pf09", "sim-meter-" + suffix)
                .replace("sim-bridge-pf09", "sim-bridge-" + suffix)
                .replace("app_simulator_pf09", "app_simulator_" + suffix)
                .replace("\"simulator-pf09\"", "\"" + appId + "\"");

        mockMvc.perform(post("/api/v1/applications/%s/deploy".formatted(appId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(get("/api/v1/objects/by-path").param("path", meterDevice))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/objects/by-path").param("path", bridgeDevice))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", DEMO_DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "virtual",
                                  "pollIntervalMs": 500,
                                  "configuration": {
                                    "litersPerSecond": "100",
                                    "filling": "true"
                                  },
                                  "autoStart": true
                                }
                                """))
                .andExpect(status().isOk());

        Thread.sleep(1500);
        mockMvc.perform(post("/api/v1/drivers/runtime/poll").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "meterLiters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(greaterThan(0.0)));

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", DEMO_DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "virtual",
                                  "pollIntervalMs": 60000,
                                  "configuration": {
                                    "tareKg": "15000",
                                    "density": "0.85",
                                    "filling": "true",
                                    "litersPerSecond": "100"
                                  },
                                  "autoStart": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/drivers/runtime/poll").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("grossWeight")));
    }
}
