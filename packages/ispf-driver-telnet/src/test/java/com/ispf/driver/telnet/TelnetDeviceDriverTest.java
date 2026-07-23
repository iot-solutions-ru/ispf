package com.ispf.driver.telnet;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelnetDeviceDriverTest {

    private ServerSocket server;
    private Thread serverThread;
    private final List<String> receivedLines = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close();
            server = null;
        }
        if (serverThread != null) {
            serverThread.join(2000);
            serverThread = null;
        }
    }

    @Test
    void executesCommandAgainstFakeTelnetServer() throws Exception {
        server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        int port = server.getLocalPort();
        serverThread = new Thread(this::serveSession, "fake-telnet-server");
        serverThread.setDaemon(true);
        serverThread.start();

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "username", "admin",
                "password", "s3cret",
                "timeoutMs", "1500"
        ));
        TelnetDeviceDriver driver = new TelnetDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("version", "show version"));

        DataRecord record = driverObject.variables.get("version");
        assertEquals("ISPF-OS 8.0.1 build 42", record.firstRow().get("value"));
        assertEquals(0, record.firstRow().get("exitCode")); // exitCode is hardcoded to 0 in v0.1
        assertEquals("", record.firstRow().get("stderr"));
        driver.disconnect();

        // The fake server observed the expected login/password/command dialogue.
        assertEquals(List.of("admin", "s3cret", "show version"), receivedLines);
    }

    @Test
    void writeIsReadOnly() {
        TelnetDeviceDriver driver = new TelnetDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("version", DataRecord.single(
                        DataSchema.builder("value")
                                .field("value", FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private void serveSession() {
        try (Socket socket = server.accept()) {
            OutputStream out = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            send(out, "FakeTelnet ready\r\nlogin: ");
            receivedLines.add(reader.readLine());
            send(out, "Password: ");
            receivedLines.add(reader.readLine());
            send(out, "\r\nWelcome, admin\r\n$ ");
            String command;
            while ((command = reader.readLine()) != null) {
                receivedLines.add(command);
                send(out, "ISPF-OS 8.0.1 build 42\r\n");
            }
        } catch (IOException ignored) {
            // client disconnect or test shutdown — best effort
        }
    }

    private static void send(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {

        private final Map<String, String> configuration;
        private final Map<String, DataRecord> variables = new HashMap<>();

        StubDriverObject(Map<String, String> configuration) {
            this.configuration = configuration;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-telnet",
                    "root.platform.devices.test",
                    ObjectType.DEVICE,
                    "Test",
                    "",
                    null
            );
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
            variables.put(name, value);
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return configuration;
        }
    }
}
