package com.ispf.driver.jdbc;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverErrorKind;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverReconnectPendingException;
import org.h2.tools.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcDeviceDriverTest {

    private JdbcDeviceDriver driver;
    private StubDriverObject driverObject;
    private String jdbcUrl;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
    }

    @Test
    void mapsSingleColumnResultToValue() throws Exception {
        startDriverWithSeededDatabase();

        driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS"));
        DataRecord record = driverObject.variables.get("sensorCount");
        assertEquals("2", record.firstRow().get("value"));

        // SELECT guard is case-insensitive
        driver.readPoints(Map.of("sensorCountLower", "select count(*) from SENSORS"));
        assertEquals("2", driverObject.variables.get("sensorCountLower").firstRow().get("value"));
    }

    @Test
    void mapsEveryRowToTheVariable() throws Exception {
        startDriverWithSeededDatabase();

        driver.readPoints(Map.of("sensors", "SELECT NAME, TEMP FROM SENSORS ORDER BY ID"));

        DataRecord record = driverObject.variables.get("sensors");
        assertEquals(2, record.rowCount());
        assertEquals("jdbcRow", record.schema().name());
        assertEquals("alpha", record.firstRow().get("NAME"));
        assertEquals("21.5", record.firstRow().get("TEMP"));
        assertEquals("beta", record.rows().get(1).get("NAME"));
        assertEquals("18.0", record.rows().get(1).get("TEMP"));
    }

    @Test
    void singleColumnKeepsEveryRowAsValue() throws Exception {
        startDriverWithSeededDatabase();

        driver.readPoints(Map.of("names", "SELECT NAME FROM SENSORS ORDER BY ID"));

        DataRecord record = driverObject.variables.get("names");
        assertEquals(2, record.rowCount());
        assertEquals("alpha", record.firstRow().get("value"));
        assertEquals("beta", record.rows().get(1).get("value"));
    }

    @Test
    void maxRowsStopsTheResult() throws Exception {
        startDriverWithSeededDatabase();
        driver.disconnect();
        driverObject = new StubDriverObject(Map.of(
                "jdbcUrl", jdbcUrl,
                "timeoutMs", "5000",
                "maxRows", "1"
        ));
        driver = new JdbcDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();

        driver.readPoints(Map.of("names", "SELECT NAME FROM SENSORS ORDER BY ID"));

        DataRecord record = driverObject.variables.get("names");
        assertEquals(1, record.rowCount());
        assertEquals("alpha", record.firstRow().get("value"));
    }

    @Test
    void emptyResultYieldsEmptyValue() throws Exception {
        startDriverWithSeededDatabase();

        driver.readPoints(Map.of("missing", "SELECT NAME FROM SENSORS WHERE 1 = 0"));

        DataRecord record = driverObject.variables.get("missing");
        assertEquals("", record.firstRow().get("value"));
    }

    @Test
    void rejectsNonSelectMapping() throws Exception {
        startDriverWithSeededDatabase();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("bad", "DELETE FROM SENSORS")));
        assertTrue(error.getMessage().contains("Only SELECT"));
    }

    @Test
    void rejectsBlankMapping() throws Exception {
        startDriverWithSeededDatabase();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("blank", "  ")));
        assertTrue(error.getMessage().contains("blank"));
    }

    @Test
    void writePointIsReadOnly() {
        driver = new JdbcDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("sensorCount", null));
        assertTrue(error.getMessage().contains("read-only"));
    }

    @Test
    void queryErrorKeepsTheOpenSession() throws Exception {
        startDriverWithSeededDatabase();

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("bad", "SELECT NOPE FROM SENSORS")));
        assertTrue(error.getMessage().contains("JDBC query failed"));
        assertTrue(driver.isConnected());

        driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS"));
        assertEquals("2", driverObject.variables.get("sensorCount").firstRow().get("value"));
    }

    @Test
    void readPointsBeforeConnectTriesConnect() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        driver = new JdbcDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of(
                "jdbcUrl", "jdbc:h2:tcp://127.0.0.1:" + closedPort + "/mem:none",
                "timeoutMs", "1000"
        )));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS")));
        assertTrue(error.getMessage().contains("JDBC connect failed"));
    }

    @Test
    void retriesConnectAfterTheBackoffWhenTheAddressReturns() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String url = tcpUrl(port, "retry" + UUID.randomUUID().toString().replace("-", ""));
        long[] now = {1_000L};
        driverObject = new StubDriverObject(Map.of("jdbcUrl", url, "timeoutMs", "1000"));
        driver = new JdbcDeviceDriver();
        driver.clock = () -> now[0];
        driver.initialize(driverObject);

        DriverException first = assertThrows(DriverException.class, driver::connect);
        assertTrue(first.getMessage().contains("JDBC connect failed"));
        DriverException waiting = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS")));
        assertTrue(waiting instanceof DriverReconnectPendingException);

        Server server = startTcp(port);
        try {
            seedSensors(url);
            now[0] = now[0] + 5_000L;
            driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS"));
            assertEquals("1", driverObject.variables.get("sensorCount").firstRow().get("value"));
        } finally {
            server.stop();
        }
    }

    @Test
    void droppedSessionWaitsThenReconnects() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        String url = tcpUrl(port, "drop" + UUID.randomUUID().toString().replace("-", ""));
        Server server = startTcp(port);
        long[] now = {1_000L};
        try {
            seedSensors(url);
            driverObject = new StubDriverObject(Map.of("jdbcUrl", url, "timeoutMs", "1000"));
            driver = new JdbcDeviceDriver();
            driver.clock = () -> now[0];
            driver.initialize(driverObject);
            driver.connect();
            assertTrue(driver.isConnected());

            server.stop();
            DriverException failed = assertThrows(DriverException.class, () ->
                    driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS")));
            assertTrue(failed.getMessage().contains("JDBC query failed"));
            assertFalse(driver.isConnected());
            DriverException waiting = assertThrows(DriverException.class, () ->
                    driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS")));
            assertTrue(waiting instanceof DriverReconnectPendingException);

            server = startTcp(port);
            seedSensors(url);
            now[0] = now[0] + 5_000L;
            driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS"));
            assertEquals("1", driverObject.variables.get("sensorCount").firstRow().get("value"));
        } finally {
            server.stop();
        }
    }

    @Test
    void configurationFailureIsNotRetried() {
        long[] now = {1_000L};
        driver = new JdbcDeviceDriver();
        driver.clock = () -> now[0];
        driver.initialize(new StubDriverObject(Map.of(
                "jdbcUrl", "jdbc:h2:tcp://plc.local/mem:none",
                "timeoutMs", "1000"
        )));
        driver.rememberConnectFailure(new DriverException(
                "JDBC connect failed", new UnknownHostException("plc.local")));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS")));
        assertEquals(DriverErrorKind.CONFIGURATION, error.kind());
        now[0] = now[0] + 120_000L;
        DriverException again = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS")));
        assertEquals(DriverErrorKind.CONFIGURATION, again.kind());
        assertTrue(again.getMessage().contains("not usable"));
    }

    private void startDriverWithSeededDatabase() throws Exception {
        String dbName = "ispf-jdbc-test-" + UUID.randomUUID().toString().replace("-", "");
        jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection seed = DriverManager.getConnection(jdbcUrl);
             Statement statement = seed.createStatement()) {
            statement.execute("CREATE TABLE SENSORS (ID INT PRIMARY KEY, NAME VARCHAR(64), TEMP DOUBLE)");
            statement.execute("INSERT INTO SENSORS (ID, NAME, TEMP) VALUES (1, 'alpha', 21.5)");
            statement.execute("INSERT INTO SENSORS (ID, NAME, TEMP) VALUES (2, 'beta', 18.0)");
        }

        driverObject = new StubDriverObject(Map.of(
                "jdbcUrl", jdbcUrl,
                "timeoutMs", "5000"
        ));
        driver = new JdbcDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        assertTrue(driver.isConnected());
    }

    private static String tcpUrl(int port, String database) {
        return "jdbc:h2:tcp://127.0.0.1:" + port + "/mem:" + database + ";DB_CLOSE_DELAY=-1";
    }

    private static Server startTcp(int port) throws Exception {
        return Server.createTcpServer("-tcpPort", Integer.toString(port), "-ifNotExists").start();
    }

    private static void seedSensors(String url) throws Exception {
        try (Connection seed = DriverManager.getConnection(url);
             Statement statement = seed.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS SENSORS (ID INT PRIMARY KEY)");
            statement.execute("MERGE INTO SENSORS (ID) KEY (ID) VALUES (1)");
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
                    "test-jdbc",
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
