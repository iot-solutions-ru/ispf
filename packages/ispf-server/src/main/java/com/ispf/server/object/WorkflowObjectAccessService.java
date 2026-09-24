package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WorkflowObjectAccessService implements WorkflowObjectAccess {

    private final ObjectManager objectManager;
    private final SystemObjectStructureService structureService;

    public WorkflowObjectAccessService(
            ObjectManager objectManager,
            SystemObjectStructureService structureService
    ) {
        this.objectManager = objectManager;
        this.structureService = structureService;
    }

    @Override
    public boolean isInitialized() {
        return objectManager.isInitialized();
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
    public void setVariableValue(String path, String name, DataRecord value) {
        objectManager.setVariableValue(path, name, value);
    }

    @Override
    public void persistNodeTree(String path) {
        objectManager.persistNodeTree(path);
    }

    @Override
    public void ensureWorkflowStructure(String path) {
        structureService.ensureWorkflowStructure(path);
    }
}
