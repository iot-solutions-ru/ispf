package com.ispf.driver.jdbc;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC driver — executes read-only SELECT queries and maps the first row to ISPF variables.
 */
public class JdbcDeviceDriver implements DeviceDriver {

    private static final DataSchema QUERY_SCHEMA = DataSchema.builder("jdbcQuery")
            .field("value", FieldType.STRING)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "jdbc",
            "JDBC Query Driver",
            "0.1.0",
            "Executes read-only SQL SELECT queries (single row) and maps columns to ISPF variables",
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
    public void connect() throws DriverException {
        try {
            DriverManager.setLoginTimeout(Math.max(1, timeoutMs / 1000));
            if (username == null || username.isBlank()) {
                connection = DriverManager.getConnection(jdbcUrl);
            } else {
                connection = DriverManager.getConnection(jdbcUrl, username, password);
            }
            connection.setReadOnly(true);
            connected = true;
            driverObject.log(DriverLogLevel.INFO, "JDBC connected to " + jdbcUrl);
        } catch (SQLException e) {
            throw new DriverException("JDBC connect failed", e);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // best effort
            }
            connection = null;
        }
    }

    @Override
    public boolean isConnected() {
        return connected && connection != null;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!isConnected()) {
            throw new DriverException("Not connected");
        }
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
        if (!normalized.regionMatches(true, 0, "SELECT", 0, 6)) {
            throw new DriverException("Only SELECT queries are allowed");
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
            throw new DriverException("JDBC query failed", e);
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
