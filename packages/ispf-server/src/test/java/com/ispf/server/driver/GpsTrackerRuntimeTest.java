package com.ispf.server.driver;

import com.ispf.server.object.ObjectManager;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
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

    private static final String DEVICE_NAME = "gps-tracker-it";
    private static final String NMEA_LINE =
            "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BlueprintApplicationService BlueprintApplicationService;

    @Autowired
    private DriverRuntimeService driverRuntimeService;

    @Autowired
    private MockMvc mockMvc;

    private String devicePath;

    private int listenPort;

    @BeforeEach
    void createDevice() {
        devicePath = DriverIntegrationTestSupport.createDevice(
                objectManager,
                BlueprintApplicationService,
                driverRuntimeService,
                DEVICE_NAME
        );
    }

    @AfterEach
    void stopDriver() throws Exception {
        DriverIntegrationTestSupport.deleteDevice(objectManager, driverRuntimeService, devicePath);
    }

    @Test
    void gpsTrackerDriverCapturesIncomingNmeaFeed() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            listenPort = probe.getLocalPort();
        }

        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", devicePath))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", devicePath)
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

        awaitPortOpen(listenPort, 10_000);

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", devicePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("gpsFeed")));

        awaitGpsFeedValue(NMEA_LINE, 20_000);
    }

    private void awaitPortOpen(int port, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress("127.0.0.1", port), 250);
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(100);
            }
        }
        throw new AssertionError("GPS tracker did not listen on port " + port + " within " + timeoutMs + "ms", last);
    }

    private void sendNmeaLine() throws Exception {
        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("127.0.0.1", listenPort), 1_000);
            client.setTcpNoDelay(true);
            OutputStream out = client.getOutputStream();
            out.write((NMEA_LINE + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            // Keep the socket open briefly so the accept/read thread can drain the line
            // before the client RST/close races the driver on overloaded CI runners.
            Thread.sleep(200);
        }
    }

    private void awaitGpsFeedValue(String expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastBody = "";
        int attempt = 0;
        while (System.currentTimeMillis() < deadline) {
            if (attempt % 3 == 0) {
                sendNmeaLine();
            }
            attempt++;
            mockMvc.perform(post("/api/v1/drivers/runtime/poll").param("devicePath", devicePath))
                    .andExpect(status().isOk());
            lastBody = mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                            .param("path", devicePath)
                            .param("name", "gpsFeed"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            if (lastBody.contains(expected)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("gpsFeed did not contain NMEA within " + timeoutMs + "ms: " + lastBody);
    }
}
