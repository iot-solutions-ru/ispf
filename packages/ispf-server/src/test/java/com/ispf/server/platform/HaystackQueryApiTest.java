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
class HaystackQueryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void queriesPointsByHaystackFilter() throws Exception {
        mockMvc.perform(get("/api/v1/platform/haystack/query")
                        .param("filter", "point and temp")
                        .param("rootPath", "root.platform.devices.lab-userA-01")
                        .param("entityKind", "point"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filter").value("point and temp"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.matches[0].path")
                        .value("root.platform.devices.lab-userA-01"))
                .andExpect(jsonPath("$.matches[0].variableName").value("sineWave"))
                .andExpect(jsonPath("$.matches[0].tags.temp").value(true))
                .andExpect(jsonPath("$.matches[0].unit").value("°C"));
    }

    @Test
    void queriesEquipByFilter() throws Exception {
        mockMvc.perform(get("/api/v1/platform/haystack/query")
                        .param("filter", "equip and lab")
                        .param("rootPath", HaystackBlueprintBootstrap.DEMO_DEVICE_PATH)
                        .param("entityKind", "equip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.matches[0].entityKind").value("equip"));
    }

    @Test
    void rejectsInvalidFilter() throws Exception {
        mockMvc.perform(get("/api/v1/platform/haystack/query")
                        .param("filter", "point or temp"))
                .andExpect(status().isBadRequest());
    }
}
