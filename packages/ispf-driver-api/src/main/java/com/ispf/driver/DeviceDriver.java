package com.ispf.driver;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Service Provider Interface for device/protocol drivers.
 * Drivers map external data sources to ISPF object variables.
 */
public interface DeviceDriver {

    DriverMetadata metadata();

    void initialize(DriverObject driverObject);

    void connect() throws DriverException;

    void disconnect();

    boolean isConnected();

    void readPoints(Map<String, String> pointMappings) throws DriverException;

    void writePoint(String pointId, DataRecord value) throws DriverException;

    interface DriverObject {
        PlatformObject deviceObject();

        void updateVariable(String name, DataRecord value);

        /** Optional device measurement time (UTC); defaults to ingest time when omitted. */
        default void updateVariable(String name, DataRecord value, Instant observedAt) {
            updateVariable(name, value);
        }

        Optional<DataRecord> getVariable(String name);

        void log(DriverLogLevel level, String message);

        /** Driver-specific configuration key/value pairs from device binding. */
        default Map<String, String> configuration() {
            return Map.of();
        }
    }

    enum DriverLogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
}
