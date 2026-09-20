package com.ispf.server.api;

import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL-contract regression for the endpoints split out of {@code ObjectController}
 * into {@code ObjectBehaviorController} and {@code ObjectEditLeaseController}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ObjectBehaviorAndLeaseApiTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String FUNCTION = "splitControllerProbeFn";
    private static final String EVENT = "splitControllerProbeEvt";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @AfterEach
    void cleanup() {
        objectManager.tree().findByPath(DEVICE).ifPresent(node -> {
            if (node.functions().containsKey(FUNCTION)) {
                objectManager.deleteFunction(DEVICE, FUNCTION);
            }
            if (node.events().containsKey(EVENT)) {
                objectManager.deleteEvent(DEVICE, EVENT);
            }
        });
    }

    @Test
    @WithMockUser(username = "admin-split", roles = "admin")
    void functionUpsertAndDeleteKeepTheirUrls() throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/functions")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"probe","sourceType":"expression","sourceBody":"1 + 1"}
                                """.formatted(FUNCTION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(FUNCTION));

        mockMvc.perform(delete("/api/v1/objects/by-path/functions")
                        .param("path", DEVICE)
                        .param("name", FUNCTION))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin-split", roles = "admin")
    void eventUpsertAndDeleteKeepTheirUrls() throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/events")
                        .param("path", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"probe","level":"INFO"}
                                """.formatted(EVENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(EVENT));

        mockMvc.perform(delete("/api/v1/objects/by-path/events")
                        .param("path", DEVICE)
                        .param("name", EVENT))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin-split", roles = "admin")
    void leaseLifecycleKeepsItsUrls() throws Exception {
        String prefix = "root.platform.devices.split-probe-" + System.nanoTime();
        mockMvc.perform(post("/api/v1/objects/leases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pathPrefix\":\"" + prefix + "\",\"ttlMinutes\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pathPrefix").value(prefix));

        mockMvc.perform(get("/api/v1/objects/leases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].pathPrefix").value(hasItem(prefix)));

        mockMvc.perform(delete("/api/v1/objects/leases").param("pathPrefix", prefix))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "operator-split", roles = "operator")
    void leasesRequireAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/objects/leases"))
                .andExpect(status().isForbidden());
    }
}
