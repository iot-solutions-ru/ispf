package com.ispf.driver.virtual;

import com.ispf.core.model.DataRecord;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMetadata;

import java.util.Map;

/**
 * Out-of-the-box virtual simulator — one poll path, no profiles.
 * <p>
 * Writes multi-type synthetic telemetry (waves, measurements with quality, nested geo/health,
 * tables, binary, meter, booleans, status). Amplitude/period knobs come from driver configuration.
 * <p>
 * Domain reference plants (Mini-TEC, tank-farm, OGP) must enrich via mixin blueprints
 * (variables + binding rules / object functions), not via driver profile switches.
 */
public class VirtualDeviceDriver implements DeviceDriver {

    private static final DriverMetadata METADATA = new DriverMetadata(
            "virtual",
            "Virtual Simulator Driver",
            "0.4.0",
            "Out-of-the-box multi-type synthetic telemetry. Domain enrichment: mixin blueprints.",
            "ISPF",
            Map.ofEntries(
                    Map.entry("baseTemperature", "22.0"),
                    Map.entry("amplitude", "15.0"),
                    Map.entry("periodSec", "60"),
                    Map.entry("litersPerSecond", "120"),
                    Map.entry("filling", "true"),
                    Map.entry("tareKg", "15000"),
                    Map.entry("density", "0.85"),
                    Map.entry("rackId", "rack-1"),
                    Map.entry("gasConnected", "true"),
                    Map.entry("groundConnected", "true"),
                    Map.entry("pollIntervalMs", "2000"),
                    Map.entry("sineAmplitude", "10.0"),
                    Map.entry("sawtoothAmplitude", "5.0"),
                    Map.entry("triangleAmplitude", "5.0"),
                    Map.entry("baseLatitude", "55.7558"),
                    Map.entry("baseLongitude", "37.6173"),
                    Map.entry("orbitRadiusM", "50"),
                    Map.entry("serialNumber", "VIRT-001"),
                    Map.entry("firmwareVersion", "1.0.0")
            )
    );

    private DriverObject driverObject;
    private final VirtualUnifiedPoll.UnifiedState unifiedState = new VirtualUnifiedPoll.UnifiedState();
    private volatile boolean connected;

    @Override
    public DriverMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(DriverObject driverObject) {
        this.driverObject = driverObject;
    }

    @Override
    public void connect() {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Virtual driver connected");
    }

    @Override
    public void disconnect() {
        connected = false;
        driverObject.log(DriverLogLevel.INFO, "Virtual driver disconnected");
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
        VirtualUnifiedPoll.poll(
                driverObject,
                unifiedState,
                VirtualUnifiedPoll.UnifiedConfig.fromMap(driverObject.configuration())
        );
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Virtual driver is read-only");
    }
}
