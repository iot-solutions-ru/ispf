package com.ispf.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IspfServerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void platformInfoIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("IoT Solutions Platform Framework"))
                .andExpect(jsonPath("$.shortName").value("ISPF"))
                .andExpect(jsonPath("$.javaVersion").isString())
                .andExpect(jsonPath("$.springBootVersion").isString())
                .andExpect(jsonPath("$.capabilities[?(@=='federation')]").exists());
    }
}
