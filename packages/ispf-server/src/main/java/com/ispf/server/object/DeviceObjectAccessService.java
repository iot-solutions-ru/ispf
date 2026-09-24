package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.spi.DeviceObjectAccess;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceObjectAccessService implements DeviceObjectAccess {

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;

    public DeviceObjectAccessService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
    }

    @Override
    public List<PlatformObject> all() {
        return objectManager.tree().all();
    }

    @Override
    public Optional<PlatformObject> findByPath(String path) {
        return objectManager.tree().findByPath(path);
    }

    @Override
    public PlatformObject require(String path) {
        return objectManager.require(path);
    }

    @Override
    public List<PlatformObject> childrenOf(String path) {
        return objectManager.tree().childrenOf(path);
    }

    @Override
    public void setSystemVariableValue(String path, String name, DataRecord value) {
        objectManager.setSystemVariableValue(path, name, value);
    }

    @Override
    public void setDriverTelemetryValue(String path, String name, DataRecord value, Instant observedAt) {
        objectManager.setDriverTelemetryValue(path, name, value, observedAt);
    }

    @Override
    public void setRuntimeVariableValue(String path, String name, DataRecord value, boolean publishEvent) {
        objectManager.setRuntimeVariableValue(path, name, value, publishEvent);
    }

    @Override
    public void publishDriverRuntimeChanged(String path) {
        objectManager.publishDriverRuntimeChanged(path);
    }

    @Override
    public void createVariable(
            String path,
            String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
        objectManager.createVariable(
                path,
                name,
                schema,
                readable,
                writable,
                initialValue,
                historyEnabled,
                historyRetentionDays
        );
    }

    @Override
    public void ensureDeviceDriverStructure(String path) {
        structureService.ensureDeviceDriverStructure(path);
    }
}
