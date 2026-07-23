package com.ispf.driver.modemat;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
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

class ModemAtDeviceDriverTest {

    private ServerSocket server;
    private Thread serverThread;
    private final List<String> receivedCommands = new CopyOnWriteArrayList<>();

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
    void pollsSignalOverTcpLoopback() throws Exception {
        server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        int port = server.getLocalPort();
        serverThread = new Thread(this::serveModem, "fake-modem");
        serverThread.setDaemon(true);
        serverThread.start();

        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "mode", "tcp",
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "timeoutMs", "2000"
        ));
        ModemAtDeviceDriver driver = new ModemAtDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("signal", "signal")); // alias maps to AT+CSQ

        DataRecord record = driverObject.variables.get("signal");
        assertEquals(Boolean.TRUE, record.firstRow().get("success"));
        assertEquals("+CSQ: 20,99", record.firstRow().get("value"));
        assertTrue(String.valueOf(record.firstRow().get("response")).contains("OK"));
        driver.disconnect();

        // The fake modem received the aliased command with CR terminator stripped.
        assertEquals(List.of("AT+CSQ"), receivedCommands);
    }

    @Test
    void writeIsReadOnly() {
        ModemAtDeviceDriver driver = new ModemAtDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("signal", DataRecord.single(
                        DataSchema.builder("value")
                                .field("value", FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private void serveModem() {
        try (Socket socket = server.accept()) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            StringBuilder command = new StringBuilder();
            int ch;
            while ((ch = in.read()) != -1) {
                if (ch == '\r') {
                    String cmd = command.toString();
                    command.setLength(0);
                    receivedCommands.add(cmd);
                    if ("AT+CSQ".equals(cmd)) {
                        out.write("+CSQ: 20,99\r\nOK\r\n".getBytes(StandardCharsets.US_ASCII));
                    } else {
                        out.write("ERROR\r\n".getBytes(StandardCharsets.US_ASCII));
                    }
                    out.flush();
                } else {
                    command.append((char) ch);
                }
            }
        } catch (IOException ignored) {
            // client disconnect or test shutdown — best effort
        }
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
                    "test-modem-at",
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
