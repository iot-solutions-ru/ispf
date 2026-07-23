package com.ispf.driver.ssh;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SshDeviceDriverTest {

    private SshServer server;
    private int port;
    private SshDeviceDriver driver;
    private StubDriverObject driverObject;

    @BeforeEach
    void setUp() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }

        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(port);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator((username, password, session) ->
                "tester".equals(username) && "s3cret".equals(password));
        server.setCommandFactory((channel, command) -> new StubCommand(command));
        server.start();

        driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "username", "tester",
                "password", "s3cret",
                "timeoutMs", "10000"
        ));
        driver = new SshDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    void executesCommandAndMapsStdout() throws Exception {
        driver.readPoints(Map.of("load", "show-load"));

        DataRecord record = driverObject.variables.get("load");
        assertEquals("load-average: 0.42", record.firstRow().get("value"));
        assertEquals(0, record.firstRow().get("exitCode"));
        assertEquals("", record.firstRow().get("stderr"));
    }

    @Test
    void mapsNonZeroExitCodeAndStderr() throws Exception {
        driver.readPoints(Map.of("probe", "fail"));

        DataRecord record = driverObject.variables.get("probe");
        assertEquals("", record.firstRow().get("value"));
        assertEquals(1, record.firstRow().get("exitCode"));
        assertEquals("boom", record.firstRow().get("stderr"));
    }

    @Test
    void authFailureIsWrappedAsDriverException() throws Exception {
        SshDeviceDriver denied = new SshDeviceDriver();
        denied.initialize(new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "username", "tester",
                "password", "wrong-password",
                "timeoutMs", "10000"
        )));
        denied.connect();

        assertThrows(DriverException.class, () -> denied.readPoints(Map.of("load", "show-load")));
    }

    @Test
    void readRequiresConnection() {
        SshDeviceDriver offline = new SshDeviceDriver();
        offline.initialize(new StubDriverObject(Map.of()));

        assertThrows(DriverException.class, () -> offline.readPoints(Map.of("load", "show-load")));
    }

    @Test
    void writeIsRejectedAsReadOnly() {
        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("load", DataRecord.single(
                        com.ispf.core.model.DataSchema.builder("value")
                                .field("value", com.ispf.core.model.FieldType.STRING)
                                .build(),
                        Map.of("value", "1")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    /**
     * Canned exec-channel command: predictable stdout/stderr/exitCode per command string.
     */
    private static final class StubCommand implements Command {

        private final String command;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback callback;

        StubCommand(String command) {
            this.command = command;
        }

        @Override
        public void setInputStream(InputStream in) {
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        @Override
        public void start(ChannelSession channel, Environment env) throws IOException {
            if ("show-load".equals(command)) {
                out.write("load-average: 0.42\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                callback.onExit(0);
            } else {
                err.write("boom\n".getBytes(StandardCharsets.UTF_8));
                err.flush();
                callback.onExit(1);
            }
        }

        @Override
        public void destroy(ChannelSession channel) {
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
                    "test-ssh",
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
