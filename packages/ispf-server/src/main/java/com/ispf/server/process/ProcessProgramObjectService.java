package com.ispf.server.process;

import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cyclic process-control program catalog (BL-172).
 */
@Service
public class ProcessProgramObjectService {

    private final ObjectManager objectManager;

    public ProcessProgramObjectService(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @Transactional
    public void ensureCatalog() {
        SystemObjectCatalogSupport.ensureFolder(
                objectManager,
                ProcessProgramPaths.PROCESS_PROGRAMS_ROOT,
                ObjectType.PROCESS_PROGRAMS,
                null
        );
    }
}
