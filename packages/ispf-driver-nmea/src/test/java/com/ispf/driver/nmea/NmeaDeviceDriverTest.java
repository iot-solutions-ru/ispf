package com.ispf.driver.nmea;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests against a local TCP server streaming NMEA 0183 sentences.
 * The driver opens a new connection per poll and reads until EOF, so the
 * server closes each connection after writing the full feed.
 */
class NmeaDeviceDriverTest {

    private static final String GGA_1 =
            "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47";
    private static final String RMC =
            "$GPRMC,123520,A,4807.100,N,01131.100,E,022.4,084.4,230394,003.1,W*6A";
    private static final String GGA_2 =
            "$GPGGA,123521,4807.050,N,01131.010,E,1,07,1.0,550.0,M,46.9,M,,*41";

    private static final List<String> SENTENCES = List.of(GGA_1, RMC, GGA_2);

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("nmeaSentence")
            .field("value", FieldType.STRING)
            .field("raw", FieldType.STRING)
            .build();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        port = serverSocket.getLocalPort();
        running = true;
        acceptThread = new Thread(this::acceptLoop, "nmea-loopback-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        running = false;
        if (serverSocket != null) {
            serverSocket.close();
            serverSocket = null;
        }
        if (acceptThread != null) {
            acceptThread.join(2000);
            acceptThread = null;
        }
    }

    @Test
    void connectsAndParsesLastMatchingSentences() throws Exception {
        StubDriverObject driverObject = driverConfig();
        NmeaDeviceDriver driver = new NmeaDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of(
                "gga", "GPGGA",
                "rmc", "GPRMC",
                "missing", "XYZ"
        ));

        DataRecord gga = driverObject.variables.get("gga");
        String ggaJson = (String) gga.firstRow().get("value");
        assertTrue(ggaJson.contains("\"type\":\"GPGGA\""), "GGA JSON was " + ggaJson);
        assertTrue(ggaJson.contains("\"f1\":\"123521\""), "last GGA must win: " + ggaJson);
        assertTrue(ggaJson.contains("\"f2\":\"4807.050\""), "GGA JSON was " + ggaJson);
        assertTrue(ggaJson.contains("\"f9\":\"550.0\""), "GGA JSON was " + ggaJson);
        assertEquals(GGA_2, gga.firstRow().get("raw"));

        DataRecord rmc = driverObject.variables.get("rmc");
        String rmcJson = (String) rmc.firstRow().get("value");
        assertTrue(rmcJson.contains("\"type\":\"GPRMC\""), "RMC JSON was " + rmcJson);
        assertTrue(rmcJson.contains("\"f1\":\"123520\""), "RMC JSON was " + rmcJson);
        assertTrue(rmcJson.contains("\"f7\":\"022.4\""), "RMC JSON was " + rmcJson);
        assertEquals(RMC, rmc.firstRow().get("raw"));

        DataRecord missing = driverObject.variables.get("missing");
        assertEquals("{}", missing.firstRow().get("value"));
        assertEquals("", missing.firstRow().get("raw"));

        driver.disconnect();
        assertFalse(driver.isConnected());
    }

    @Test
    void reconnectsPerPoll() throws Exception {
        StubDriverObject driverObject = driverConfig();
        NmeaDeviceDriver driver = new NmeaDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        for (int i = 0; i < 2; i++) {
            driver.readPoints(Map.of("rmc", "GPRMC"));
            DataRecord record = driverObject.variables.get("rmc");
            assertTrue(((String) record.firstRow().get("value")).contains("\"type\":\"GPRMC\""));
            assertEquals(RMC, record.firstRow().get("raw"));
        }
        driver.disconnect();
    }

    @Test
    void sentenceTypeMatchingIsPrefixBased() throws Exception {
        StubDriverObject driverObject = driverConfig();
        NmeaDeviceDriver driver = new NmeaDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        // "GGA" is not a prefix of "GPGGA": matching uses startsWith on the
        // full type token, so short forms never match talker-prefixed types.
        driver.readPoints(Map.of("shortForm", "GGA"));
        assertEquals("{}", driverObject.variables.get("shortForm").firstRow().get("value"));

        // "GP" is a prefix of both GPGGA and GPRMC; the last match wins.
        driver.readPoints(Map.of("anyGp", "GP"));
        DataRecord anyGp = driverObject.variables.get("anyGp");
        assertTrue(((String) anyGp.firstRow().get("value")).contains("\"type\":\"GPGGA\""));
        assertEquals(GGA_2, anyGp.firstRow().get("raw"));
        driver.disconnect();
    }

    @Test
    void blankSentenceTypeIsRejected() throws Exception {
        StubDriverObject driverObject = driverConfig();
        NmeaDeviceDriver driver = new NmeaDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("gga", "  ")));
        assertTrue(error.getMessage().contains("blank"));
        driver.disconnect();
    }

    @Test
    void readWithoutConnectFails() {
        NmeaDeviceDriver driver = new NmeaDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("gga", "GGA")));
        assertEquals("Not connected", error.getMessage());
    }

    @Test
    void unreachableFeedFails() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", String.valueOf(closedPort)
        ));
        NmeaDeviceDriver driver = new NmeaDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("gga", "GGA")));
        assertTrue(error.getMessage().contains("NMEA TCP read failed"));
        driver.disconnect();
    }

    @Test
    void writeIsReadOnly() {
        NmeaDeviceDriver driver = new NmeaDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("gga", DataRecord.single(VALUE_SCHEMA,
                        Map.of("value", "{}", "raw", ""))));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                streamAndClose(socket);
            } catch (SocketException e) {
                return; // server socket closed during shutdown
            } catch (IOException e) {
                if (!running) {
                    return;
                }
            }
        }
    }

    private void streamAndClose(Socket socket) {
        try (socket; OutputStream out = socket.getOutputStream()) {
            for (String sentence : SENTENCES) {
                out.write((sentence + "\r\n").getBytes(StandardCharsets.US_ASCII));
            }
            out.flush();
        } catch (IOException ignored) {
            // client went away mid-stream
        }
    }

    private StubDriverObject driverConfig() {
        Map<String, String> configuration = new HashMap<>();
        configuration.put("host", "127.0.0.1");
        configuration.put("port", String.valueOf(port));
        return new StubDriverObject(configuration);
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
                    "test-nmea",
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
