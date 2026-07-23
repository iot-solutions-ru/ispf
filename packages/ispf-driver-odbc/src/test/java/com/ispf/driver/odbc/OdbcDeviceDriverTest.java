package com.ispf.driver.odbc;

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

/**
 * Exercises the driver's JDBC-wrapper logic against a real H2 in-memory database.
 * This does NOT test a real ODBC-JDBC bridge driver (none ships with the JDK).
 */
class OdbcDeviceDriverTest {

    private OdbcDeviceDriver driver;
    private StubDriverObject driverObject;

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.disconnect();
            driver = null;
        }
    }

    @Test
    void mapsConfiguredQueryFirstRowToPoints() throws Exception {
        startDriverWithSeededDatabase("SELECT ID, NAME FROM DEVICE ORDER BY ID");

        driver.readPoints(Map.of(
                "deviceId", "ID",
                "deviceName", "NAME"
        ));

        DataRecord id = driverObject.variables.get("deviceId");
        assertEquals("odbcValue", id.schema().name());
        assertEquals(1, id.rowCount());
        assertEquals("1", id.firstRow().get("value"));
        assertEquals("alpha", driverObject.variables.get("deviceName").firstRow().get("value"));
    }

    @Test
    void resolvesColumnNameCaseInsensitively() throws Exception {
        startDriverWithSeededDatabase("SELECT ID, NAME FROM DEVICE ORDER BY ID");

        driver.readPoints(Map.of("deviceName", "name"));

        assertEquals("alpha", driverObject.variables.get("deviceName").firstRow().get("value"));
    }

    @Test
    void missingColumnYieldsEmptyValue() throws Exception {
        startDriverWithSeededDatabase("SELECT ID, NAME FROM DEVICE ORDER BY ID");

        driver.readPoints(Map.of("missing", "NO_SUCH_COLUMN"));

        assertEquals("", driverObject.variables.get("missing").firstRow().get("value"));
    }

    @Test
    void rejectsNonSelectConfiguredQuery() throws Exception {
        startDriverWithSeededDatabase("DELETE FROM DEVICE");

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("deviceId", "ID")));
        assertTrue(error.getMessage().contains("Only SELECT"));
    }

    @Test
    void readPointsRequiresConnection() {
        driver = new OdbcDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.readPoints(Map.of("deviceId", "ID")));
        assertTrue(error.getMessage().contains("Not connected"));
    }

    @Test
    void writePointIsReadOnly() {
        driver = new OdbcDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));

        DriverException error = assertThrows(DriverException.class, () ->
                driver.writePoint("deviceId", null));
        assertTrue(error.getMessage().contains("read-only"));
    }

    private void startDriverWithSeededDatabase(String configuredQuery) throws Exception {
        String dbName = "ispf-odbc-test-" + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection seed = DriverManager.getConnection(jdbcUrl);
             Statement statement = seed.createStatement()) {
            statement.execute("CREATE TABLE DEVICE (ID INT PRIMARY KEY, NAME VARCHAR(64))");
            statement.execute("INSERT INTO DEVICE (ID, NAME) VALUES (1, 'alpha')");
            statement.execute("INSERT INTO DEVICE (ID, NAME) VALUES (2, 'beta')");
        }

        driverObject = new StubDriverObject(Map.of(
                "jdbcUrl", jdbcUrl,
                "query", configuredQuery,
                "timeoutMs", "5000"
        ));
        driver = new OdbcDeviceDriver();
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
                    "test-odbc",
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
