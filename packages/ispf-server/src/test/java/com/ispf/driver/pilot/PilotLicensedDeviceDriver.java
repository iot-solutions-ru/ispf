package com.ispf.driver.pilot;

import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.util.Map;

/**
 * Minimal commercial-pack pilot driver for FW-50 integration tests (not bundled in server classpath).
 */
public class PilotLicensedDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "pilot-licensed",
            "Pilot Licensed Driver",
            "0.1.0",
            "FW-50 staging pilot — connectivity shell for licensed pack loader tests",
            "ISPF Commercial Pilot",
            Map.of("pollIntervalMs", "5000")
    );

    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        connected = false;
    }

    @Override
    public void connect() throws DriverException {
        connected = true;
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        if (!connected) {
            throw new DriverException("Not connected");
        }
    }

    @Override
    public void writePoint(String pointId, com.ispf.core.model.DataRecord value) throws DriverException {
        throw new DriverException("Read-only pilot driver");
    }
}
