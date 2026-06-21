package com.ispf.server.driver;

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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlexibleDriverRuntimeTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    private ServerSocket echoServer;
    private ExecutorService echoExecutor;
    private int echoPort;

    @BeforeEach
    void startEchoServer() throws IOException {
        echoServer = new ServerSocket(0);
        echoPort = echoServer.getLocalPort();
        echoExecutor = Executors.newSingleThreadExecutor();
        echoExecutor.submit(() -> {
            while (!echoServer.isClosed()) {
                try (Socket client = echoServer.accept()) {
                    InputStream in = client.getInputStream();
                    OutputStream out = client.getOutputStream();
                    byte[] buffer = new byte[256];
                    int read = in.read(buffer);
                    if (read > 0) {
                        out.write("TEMP=23.5\r\n".getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                } catch (IOException e) {
                    if (!echoServer.isClosed()) {
                        break;
                    }
                }
            }
        });
    }

    @AfterEach
    void stopEchoServer() throws Exception {
        try {
            mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE));
        } catch (Exception ignored) {
            // best effort
        }
        if (echoServer != null) {
            echoServer.close();
        }
        if (echoExecutor != null) {
            echoExecutor.shutdownNow();
            echoExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void flexibleDriverReadsTcpResponseWithRegexCapture() throws Exception {
        mockMvc.perform(post("/api/v1/drivers/runtime/stop").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/drivers/runtime/configure")
                        .param("devicePath", DEMO_DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "flexible",
                                  "pollIntervalMs": 60000,
                                  "configuration": {
                                    "protocol": "TCP",
                                    "host": "127.0.0.1",
                                    "port": "%d",
                                    "timeoutMs": "5000",
                                    "encoding": "utf8"
                                  },
                                  "pointMappings": {
                                    "flexTemp": "READ:TEMP=([0-9.]+)"
                                  },
                                  "autoStart": true
                                }
                                """.formatted(echoPort)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.driverId").value("flexible"));

        mockMvc.perform(post("/api/v1/drivers/runtime/poll").param("devicePath", DEMO_DEVICE))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects/by-path/variables")
                        .param("path", DEMO_DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("flexTemp")));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", DEMO_DEVICE)
                        .param("name", "flexTemp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value("23.5"));
    }
}
