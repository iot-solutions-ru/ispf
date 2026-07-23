package com.ispf.driver.smpp;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests against an in-test SMPP 3.4 server speaking raw PDUs on a ServerSocket:
 * bind_transceiver / submit_sm / unbind / enquire_link are answered with real response PDUs.
 */
class SmppDeviceDriverTest {

    @Test
    void bindPointReportsBound() throws Exception {
        try (FakeSmppServer server = new FakeSmppServer()) {
            StubDriverObject driverObject = driverConfig(server.port());
            SmppDeviceDriver driver = new SmppDeviceDriver();
            driver.initialize(driverObject);
            driver.connect();
            assertTrue(driver.isConnected());

            driver.readPoints(Map.of("status", "bind"));

            DataRecord record = driverObject.variables.get("status");
            assertEquals("bound", record.firstRow().get("value"));
            assertEquals(true, record.firstRow().get("bound"));
            assertEquals("", record.firstRow().get("messageId"));
            assertEquals(1, server.bindCount());
            driver.disconnect();
            assertFalse(driver.isConnected());
        }
    }

    @Test
    void submitPointDeliversMessageAndSurfacesMessageId() throws Exception {
        try (FakeSmppServer server = new FakeSmppServer()) {
            StubDriverObject driverObject = driverConfig(server.port());
            SmppDeviceDriver driver = new SmppDeviceDriver();
            driver.initialize(driverObject);
            driver.connect();

            driver.readPoints(Map.of("alert", "+15551234567:hello ispf"));

            DataRecord record = driverObject.variables.get("alert");
            assertEquals("sent", record.firstRow().get("value"));
            assertEquals(true, record.firstRow().get("bound"));
            assertEquals("msg-1", record.firstRow().get("messageId"));

            assertEquals(1, server.submissions().size());
            FakeSmppServer.Submission submission = server.submissions().getFirst();
            // SMPP submit_sm: source_addr carries the sender id (systemId),
            // destination_addr carries the point's destination MSISDN.
            assertEquals("+15551234567", submission.destination());
            assertEquals("ispf-test", submission.source());
            assertEquals("hello ispf", submission.shortMessage());
            driver.disconnect();
        }
    }

    @Test
    void bindPointAgainstDeadServerReportsUnbound() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StubDriverObject driverObject = driverConfig(closedPort);
        SmppDeviceDriver driver = new SmppDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("status", "bind"));

        DataRecord record = driverObject.variables.get("status");
        assertEquals("unbound", record.firstRow().get("value"));
        assertEquals(false, record.firstRow().get("bound"));
        assertEquals("", record.firstRow().get("messageId"));
        driver.disconnect();
    }

    @Test
    void submitAgainstDeadServerFails() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StubDriverObject driverObject = driverConfig(closedPort);
        SmppDeviceDriver driver = new SmppDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("alert", "+15551234567:hello")));
        assertTrue(error.getMessage().contains("submit failed"));
        driver.disconnect();
    }

    @Test
    void readWithoutConnectFails() {
        SmppDeviceDriver driver = new SmppDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("status", "bind")));
        assertEquals("Not connected", error.getMessage());
    }

    @Test
    void writeIsReadOnly() {
        SmppDeviceDriver driver = new SmppDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("status", DataRecord.single(
                        DataSchema.builder("smppResult")
                                .field("value", FieldType.STRING)
                                .field("bound", FieldType.BOOLEAN)
                                .field("messageId", FieldType.STRING)
                                .build(),
                        Map.of("value", "bound", "bound", true, "messageId", "")
                )));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private StubDriverObject driverConfig(int port) {
        return new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(port),
                "systemId", "ispf-test",
                "password", "secret"
        ));
    }

    /**
     * Minimal SMPP 3.4 peer: 16-byte PDU header (command_length, command_id,
     * command_status, sequence_number), answers bind_transceiver, submit_sm,
     * unbind and enquire_link with real response PDUs.
     */
    private static final class FakeSmppServer implements AutoCloseable {

        private static final int BIND_TRANSCEIVER = 0x00000009;
        private static final int BIND_TRANSCEIVER_RESP = 0x80000009;
        private static final int SUBMIT_SM = 0x00000004;
        private static final int SUBMIT_SM_RESP = 0x80000004;
        private static final int UNBIND = 0x00000006;
        private static final int UNBIND_RESP = 0x80000006;
        private static final int ENQUIRE_LINK = 0x00000015;
        private static final int ENQUIRE_LINK_RESP = 0x80000015;

        record Submission(String destination, String source, String shortMessage) {
        }

        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final List<Socket> connections = new CopyOnWriteArrayList<>();
        private final List<Submission> submissions = new CopyOnWriteArrayList<>();
        private volatile int bindCount;
        private int messageCounter;

        FakeSmppServer() throws IOException {
            serverSocket = new ServerSocket(0);
            acceptThread = new Thread(this::acceptLoop, "smpp-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int bindCount() {
            return bindCount;
        }

        List<Submission> submissions() {
            return submissions;
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    connections.add(socket);
                    Thread handler = new Thread(() -> serve(socket), "smpp-session");
                    handler.setDaemon(true);
                    handler.start();
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        throw new IllegalStateException("SMPP server accept failed", e);
                    }
                }
            }
        }

        private void serve(Socket socket) {
            try {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                while (!socket.isClosed()) {
                    int length;
                    try {
                        length = in.readInt();
                    } catch (EOFException e) {
                        return;
                    }
                    if (length < 16 || length > 64 * 1024) {
                        return;
                    }
                    int commandId = in.readInt();
                    in.readInt(); // command_status, always 0 in requests
                    int sequence = in.readInt();
                    byte[] body = in.readNBytes(length - 16);
                    if (body.length != length - 16) {
                        return;
                    }
                    switch (commandId) {
                        case BIND_TRANSCEIVER -> {
                            bindCount++;
                            writePdu(out, BIND_TRANSCEIVER_RESP, sequence, cstring("ispf-smsc"));
                        }
                        case SUBMIT_SM -> {
                            submissions.add(parseSubmission(body));
                            messageCounter++;
                            writePdu(out, SUBMIT_SM_RESP, sequence, cstring("msg-" + messageCounter));
                        }
                        case UNBIND -> writePdu(out, UNBIND_RESP, sequence, new byte[0]);
                        case ENQUIRE_LINK -> writePdu(out, ENQUIRE_LINK_RESP, sequence, new byte[0]);
                        default -> {
                            // unknown request: no response, client times out
                        }
                    }
                }
            } catch (IOException ignored) {
                // connection closed by client
            }
        }

        /** Parses source_addr, destination_addr and short_message out of a submit_sm body. */
        private static Submission parseSubmission(byte[] body) {
            int offset = 0;
            offset = skipCstring(body, offset);       // service_type
            offset += 2;                              // source_addr_ton, source_addr_npi
            int sourceStart = offset;
            offset = skipCstring(body, offset);       // source_addr
            String source = new String(body, sourceStart, offset - sourceStart - 1, StandardCharsets.US_ASCII);
            offset += 2;                              // dest_addr_ton, dest_addr_npi
            int destStart = offset;
            offset = skipCstring(body, offset);       // destination_addr
            String destination = new String(body, destStart, offset - destStart - 1, StandardCharsets.US_ASCII);
            offset += 3;                              // esm_class, protocol_id, priority_flag
            offset = skipCstring(body, offset);       // schedule_delivery_time
            offset = skipCstring(body, offset);       // validity_period
            offset += 3;                              // registered_delivery, replace_if_present_flag, data_coding
            offset += 1;                              // sm_default_msg_id
            int smLength = body[offset] & 0xFF;
            String message = new String(body, offset + 1, smLength, StandardCharsets.UTF_8);
            return new Submission(destination, source, message);
        }

        private static int skipCstring(byte[] body, int offset) {
            while (offset < body.length && body[offset] != 0) {
                offset++;
            }
            return offset + 1;
        }

        private static byte[] cstring(String value) {
            byte[] ascii = value.getBytes(StandardCharsets.US_ASCII);
            byte[] result = new byte[ascii.length + 1];
            System.arraycopy(ascii, 0, result, 0, ascii.length);
            return result;
        }

        private static void writePdu(DataOutputStream out, int commandId, int sequence, byte[] body)
                throws IOException {
            synchronized (out) {
                out.writeInt(16 + body.length);
                out.writeInt(commandId);
                out.writeInt(0);
                out.writeInt(sequence);
                out.write(body);
                out.flush();
            }
        }

        @Override
        public void close() throws IOException {
            for (Socket socket : connections) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
            serverSocket.close();
            try {
                acceptThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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
                    "test-smpp",
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
