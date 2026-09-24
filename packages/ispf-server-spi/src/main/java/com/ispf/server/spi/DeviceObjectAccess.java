package com.ispf.server.spi;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.PlatformObject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Object-tree reads and driver-variable writes used by the driver runtime.
 * Implemented beside {@code ObjectManager}; the driver package must not import it.
 */
public interface DeviceObjectAccess {

    List<PlatformObject> all();

    Optional<PlatformObject> findByPath(String path);

    PlatformObject require(String path);

    List<PlatformObject> childrenOf(String path);

    void setSystemVariableValue(String path, String name, DataRecord value);

    void setDriverTelemetryValue(String path, String name, DataRecord value, Instant observedAt);

    void setRuntimeVariableValue(String path, String name, DataRecord value, boolean publishEvent);

    void publishDriverRuntimeChanged(String path);

    void createVariable(
            String path,
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays
    );

    void ensureDeviceDriverStructure(String path);
}
