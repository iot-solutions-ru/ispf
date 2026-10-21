package com.ispf.server.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UnknownDriverCreateRollbackTest {

    private static final String PATH = "root.platform.devices.unknown-driver-create";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownDriverIdDoesNotLeaveTheObject() throws Exception {
        mockMvc.perform(post("/api/v1/objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "name": "unknown-driver-create",
                                  "type": "DEVICE",
                                  "displayName": "unknown driver",
                                  "autoApplyMixinBlueprints": false,
                                  "driverId": "no-such-driver",
                                  "autoStartDriver": false
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/objects/by-path").param("path", PATH))
                .andExpect(status().isNotFound());
    }
}
