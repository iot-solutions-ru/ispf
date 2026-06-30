package com.ispf.server.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrickExportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportsJsonLdForLabDemoDevice() throws Exception {
        mockMvc.perform(get("/api/v1/platform/brick/export")
                        .param("rootPath", "root.platform.devices.lab-userA-01")
                        .param("format", "jsonld")
                        .param("includePoints", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formatVersion").value(1))
                .andExpect(jsonPath("$.format").value("jsonld"))
                .andExpect(jsonPath("$.entityCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$['@graph'][?(@['ispf:path'] == 'root.platform.devices.lab-userA-01')]['@type']")
                        .value("brick:Sensor"))
                .andExpect(jsonPath("$['@graph'][?(@['ispf:path'] == 'root.platform.devices.lab-userA-01.sineWave')]['@type']")
                        .value("brick:Temperature_Sensor"))
                .andExpect(jsonPath("$['@graph'][?(@['ispf:path'] == 'root.platform.devices.lab-userA-01')]['brick:hasPoint'][0]['@id']")
                        .exists());
    }

    @Test
    void exportsTurtleForLabDemoDevice() throws Exception {
        mockMvc.perform(get("/api/v1/platform/brick/export")
                        .param("rootPath", "root.platform.devices.lab-userA-01")
                        .param("format", "turtle")
                        .param("includePoints", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("@prefix brick:")))
                .andExpect(content().string(containsString("brick:hasPoint")))
                .andExpect(content().string(containsString("brick:Sensor")))
                .andExpect(content().string(containsString("brick:Temperature_Sensor")));
    }
}
