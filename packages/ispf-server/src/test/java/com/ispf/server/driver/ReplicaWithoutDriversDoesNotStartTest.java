package com.ispf.server.driver;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.cluster.enabled=true",
        "ispf.cluster.replica-profile=edge-api",
        "ispf.nats.replica-id=edge-api-no-drivers"
})
class ReplicaWithoutDriversDoesNotStartTest {

    private static final String PATH = "root.platform.devices.edge-api-no-drivers";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private DeviceProvisioningService deviceProvisioningService;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @Autowired
    private DriverOwnershipService ownershipService;

    @AfterEach
    void cleanup() {
        try {
            objectManager.delete(PATH);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    @Test
    void edgeApiDoesNotStartOrPollTheDevice() throws Exception {
        assertFalse(ownershipService.runsDriversLocally());
        objectManager.create(
                "root.platform.devices",
                "edge-api-no-drivers",
                ObjectType.DEVICE,
                "edge api probe",
                null,
                null
        );
        deviceProvisioningService.provisionDriver(PATH, "virtual", 5000, false);
        driverRuntimeService.setDriverAutoStart(PATH, true);

        driverRuntimeService.startConfiguredDrivers();
        assertFalse(driverRuntimeService.isActiveLocally(PATH));

        mockMvc.perform(post("/api/v1/drivers/runtime/start").param("devicePath", PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This replica does not run drivers (no DRIVERS capability)"));

        assertFalse(driverRuntimeService.isActiveLocally(PATH));
        assertTrue(driverRuntimeService.activeDevicePaths().isEmpty());
    }
}
