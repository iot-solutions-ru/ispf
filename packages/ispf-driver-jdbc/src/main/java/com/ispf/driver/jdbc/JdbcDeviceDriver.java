package com.ispf.driver.jdbc;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverConfigurationException;
import com.ispf.driver.DriverErrorKind;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;
import com.ispf.driver.DriverReconnectPendingException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * JDBC driver — executes read-only SELECT queries and maps the first row to ISPF variables.
 * <p>
 * A connect failure that is not a configuration error is retried from later polls. The wait
 * grows from 5 seconds to 60 seconds. A query that finds the session closed drops it and uses
 * the same wait. A session that is still open after a query error stays open.
 */
public class JdbcDeviceDriver implements DeviceDriver {

    private static final long[] RETRY_DELAYS_MS = {5_000L, 10_000L, 20_000L, 40_000L, 60_000L};

    private static final DataSchema QUERY_SCHEMA = DataSchema.builder("jdbcQuery")
            .field("value", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "jdbc",
            "JDBC Query Driver",
            "0.1.1",
            "Read-only SQL SELECT. Retries connect unless configuration is wrong",
            "ISPF",
            Map.of(
                    "jdbcUrl", "jdbc:h2:mem:test",
                    "username", "",
                    "password", "",
                    "timeoutMs", "5000",
                    "pollIntervalMs", "30000"
            )
    );

    private DriverObject driverObject;
    private String jdbcUrl = "jdbc:h2:mem:test";
    private String username = "";
    private String password = "";
    private int timeoutMs = 5000;
    private Connection connection;
    private final Map<String, String> points = new ConcurrentHashMap<>();
    private volatile boolean connected;
    private DriverErrorKind lastConnectFailure;
    private int retryAttempt;
    private long nextConnectAttemptAt;
    LongSupplier clock = System::currentTimeMillis;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
        driverObject.configuration().forEach(this::applyConfig);
    }

    private void applyConfig(String key, String value) {
        if (value == null) {
            return;
        }
        switch (key) {
            case "jdbcUrl" -> jdbcUrl = value.trim();
            case "username" -> username = value;
            case "password" -> password = value;
            case "timeoutMs" -> timeoutMs = Integer.parseInt(value.trim());
            default -> { }
        }
    }

    @Override
    public boolean retriesConnectWhilePolling() {
        return true;
    }

    @Override
    public void connect() throws DriverException {
        int previousLoginTimeout = DriverManager.getLoginTimeout();
        try {
            closeConnection();
            // DriverManager login timeout is JVM-global — restore prior value after connect.
            DriverManager.setLoginTimeout(Math.max(1, timeoutMs / 1000));
            if (username == null || username.isBlank()) {
                connection = DriverManager.getConnection(jdbcUrl);
            } else {
                connection = DriverManager.getConnection(jdbcUrl, username, password);
            }
            connection.setReadOnly(true);
            connected = true;
            clearConnectFailure();
            driverObject.log(DriverLogLevel.INFO, "JDBC connected to " + jdbcUrl);
        } catch (SQLException e) {
            connected = false;
            closeConnection();
            DriverException error = new DriverException("JDBC connect failed", e);
            rememberConnectFailure(error);
            throw error;
        } finally {
            DriverManager.setLoginTimeout(previousLoginTimeout);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        clearConnectFailure();
        closeConnection();
        points.clear();
    }

    @Override
    public boolean isConnected() {
        if (!connected || connection == null) {
            return false;
        }
        try {
            return !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        ensureConnectedForPoll();
        points.clear();
        for (Map.Entry<String, String> entry : pointMappings.entrySet()) {
            String sql = entry.getValue();
            if (sql == null || sql.isBlank()) {
                throw new DriverException("JDBC query mapping is blank for " + entry.getKey());
            }
            points.put(entry.getKey(), sql);
            driverObject.updateVariable(entry.getKey(), executeQuery(sql));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("JDBC driver is read-only in v0.1");
    }

    private DataRecord executeQuery(String sql) throws DriverException {
        String normalized = sql.trim();
        if (!normalized.regionMatches(true, 0, "SELECT", 0, 6)
                && !normalized.regionMatches(true, 0, "WITH", 0, 4)) {
            throw new DriverException("Only SELECT/WITH queries are allowed");
        }
        // Reject multi-statement payloads (e.g. SELECT 1; DROP TABLE …).
        String withoutTrailingSemi = normalized.endsWith(";")
                ? normalized.substring(0, normalized.length() - 1).trim()
                : normalized;
        if (withoutTrailingSemi.indexOf(';') >= 0) {
            throw new DriverException("Multiple SQL statements are not allowed");
        }
        String upper = withoutTrailingSemi.toUpperCase(java.util.Locale.ROOT);
        for (String banned : List.of(" INSERT ", " UPDATE ", " DELETE ", " DROP ", " ALTER ", " TRUNCATE ", " CREATE ", " GRANT ", " REVOKE ")) {
            if (upper.contains(banned)) {
                throw new DriverException("SQL contains disallowed keyword:" + banned.trim());
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(Math.max(1, timeoutMs / 1000));
            try (ResultSet rs = statement.executeQuery(normalized)) {
                if (!rs.next()) {
                    return DataRecord.single(QUERY_SCHEMA, Map.of("value", ""));
                }
                Map<String, Object> fields = new LinkedHashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    String label = meta.getColumnLabel(i);
                    Object val = rs.getObject(i);
                    fields.put(sanitizeFieldName(label), val == null ? "" : String.valueOf(val));
                }
                if (fields.size() == 1) {
                    return DataRecord.single(QUERY_SCHEMA, Map.of("value", fields.values().iterator().next()));
                }
                DataSchema schema = buildSchema(fields);
                return DataRecord.single(schema, fields);
            }
        } catch (SQLException e) {
            DriverException error = new DriverException("JDBC query failed", e);
            if (!connectionStillUsable()) {
                rememberConnectFailure(error);
                closeConnection();
            }
            throw error;
        }
    }

    private void ensureConnectedForPoll() throws DriverException {
        if (isConnected()) {
            return;
        }
        if (lastConnectFailure == DriverErrorKind.CONFIGURATION) {
            throw new DriverConfigurationException("JDBC address is not usable");
        }
        if (clock.getAsLong() < nextConnectAttemptAt) {
            throw new DriverReconnectPendingException("JDBC reconnect is waiting");
        }
        connect();
    }

    void rememberConnectFailure(DriverException error) {
        lastConnectFailure = DriverErrorKind.classify(error);
        long delay = RETRY_DELAYS_MS[Math.min(retryAttempt, RETRY_DELAYS_MS.length - 1)];
        retryAttempt++;
        nextConnectAttemptAt = clock.getAsLong() + delay;
    }

    private void clearConnectFailure() {
        lastConnectFailure = null;
        retryAttempt = 0;
        nextConnectAttemptAt = 0;
    }

    private void closeConnection() {
        Connection current = connection;
        connection = null;
        connected = false;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    private boolean connectionStillUsable() {
        Connection current = connection;
        if (current == null) {
            return false;
        }
        try {
            return !current.isClosed() && current.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    private static DataSchema buildSchema(Map<String, Object> fields) {
        DataSchema.Builder builder = DataSchema.builder("jdbcRow");
        for (String name : fields.keySet()) {
            builder.field(name, FieldType.STRING);
        }
        return builder.build();
    }

    private static String sanitizeFieldName(String label) {
        return label.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
