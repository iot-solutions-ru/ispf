package com.ispf.server.spi;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.PlatformObject;

import java.util.List;
import java.util.Optional;

/**
 * Object-tree reads and workflow-variable writes used by the workflow package.
 * Implemented beside {@code ObjectManager}; the workflow package must not import it.
 */
public interface WorkflowObjectAccess {

    boolean isInitialized();

    List<PlatformObject> all();

    Optional<PlatformObject> findByPath(String path);

    PlatformObject require(String path);

    List<PlatformObject> childrenOf(String path);

    void setVariableValue(String path, String name, DataRecord value);

    void persistNodeTree(String path);

    void ensureWorkflowStructure(String path);
}
