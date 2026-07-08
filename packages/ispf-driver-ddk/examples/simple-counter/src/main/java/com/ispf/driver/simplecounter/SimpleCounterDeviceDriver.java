package com.ispf.driver.simplecounter;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reference DDK driver — monotonic counter per mapped point (BL-144).
 */
public class SimpleCounterDeviceDriver implements DeviceDriver {

    private static final DataSchema VALUE_SCHEMA = DataSchema.builder("simpleCounterValue")
            .field("count", FieldType.LONG)
            .field("connected", FieldType.BOOLEAN)
            .build();

    private static final DriverMetadata METADATA = new DriverMetadata(
            "simple-counter",
            "Simple Counter Driver (DDK reference)",
            "0.1.0",
            "Increments an in-memory counter on each poll — no external hardware",
            "ISPF",
            Map.of("pollIntervalMs", "1000"),
            DriverMaturity.BETA,
            Set.of("read")
    );

    private DriverObject driverObject;
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
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
    public void connect() throws DriverException {
        connected = true;
        driverObject.log(DriverLogLevel.INFO, "Simple counter driver connected");
    }

    @Override
    public void disconnect() {
        connected = false;
        counters.clear();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void readPoints(Map<String, String> pointMappings) throws DriverException {
        requireConnected();
        for (String variableName : pointMappings.keySet()) {
            AtomicLong counter = counters.computeIfAbsent(variableName, ignored -> new AtomicLong());
            long value = counter.incrementAndGet();
            driverObject.updateVariable(variableName, DataRecord.single(VALUE_SCHEMA, Map.of(
                    "count", value,
                    "connected", true
            )));
        }
    }

    @Override
    public void writePoint(String pointId, DataRecord value) throws DriverException {
        throw new DriverException("Simple counter driver is read-only");
    }

    private void requireConnected() throws DriverException {
        if (!connected) {
            throw new DriverException("Not connected");
        }
    }
}
