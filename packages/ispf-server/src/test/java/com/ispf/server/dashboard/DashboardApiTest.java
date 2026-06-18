package com.ispf.server.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void demoDashboardHasLayoutAndWidgets() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/by-path")
                        .param("path", "root.platform.dashboards.demo-sensor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Demo Sensor Dashboard"))
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='temp-value')]").exists())
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='temp-trend')]").exists())
                .andExpect(jsonPath("$.layout.widgets[?(@.id=='temp-trend')].type").value("chart"));
    }

    @Test
    void listsDashboardModel() throws Exception {
        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='dashboard-v1')]").exists());
    }
}
