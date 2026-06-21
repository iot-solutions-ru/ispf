package com.ispf.server.driver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GpsTrackerRuntimeTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String NMEA_LINE =
            "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47";

    @Autowired
    private MockMvc mockMvc;

    private int listenPort;

    @AfterEach
    void stopDriver() throws Exception {
        try {
            mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE));
        } catch (Exception ignored) {
            // best effort
        }
    }

    @Test
    void gpsTrackerDriverCapturesIncomingNmeaFeed() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            listenPort = probe.getLocalPort();
        }

        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", DEMO_DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "gps-tracker",
                                  "pollIntervalMs": 60000,
                                  "configuration": {
                                    "listenPort": "%d",
                                    "bufferSize": "4096"
                                  },
                                  "pointMappings": {
                                    "gpsFeed": "feed"
                                  },
                                  "autoStart": true
                                }
                                """.formatted(listenPort)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.driverId").value("gps-tracker"));

        Thread.sleep(200);
        try (Socket client = new Socket("127.0.0.1", listenPort)) {
            OutputStream out = client.getOutputStream();
            out.write((NMEA_LINE + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            Thread.sleep(300);
        }

        mockMvc.perform(post("/api/v1/drivers/runtime/poll").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("gpsFeed")));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "gpsFeed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(NMEA_LINE));
    }
}
