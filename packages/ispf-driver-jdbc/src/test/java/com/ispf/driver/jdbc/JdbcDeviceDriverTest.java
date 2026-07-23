package com.ispf.driver.jdbc;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void mapsFirstRowColumnsToDynamicSchema() throws Exception {
        startDriverWithSeededDatabase();

        driver.readPoints(Map.of("firstSensor", "SELECT NAME, TEMP FROM SENSORS ORDER BY ID"));

        DataRecord record = driverObject.variables.get("firstSensor");
        assertEquals(1, record.rowCount());
        assertEquals("jdbcRow", record.schema().name());
        assertEquals("alpha", record.firstRow().get("NAME"));
        assertEquals("21.5", record.firstRow().get("TEMP"));
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
    void readPointsRequiresConnection() {
        driver = new JdbcDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("sensorCount", "SELECT COUNT(*) FROM SENSORS")));
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
