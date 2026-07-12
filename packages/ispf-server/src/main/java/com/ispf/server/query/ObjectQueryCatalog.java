package com.ispf.server.query;

import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.SystemObjectCatalogSupport;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform catalog folder for object-query functions ({@code root.platform.queries}).
 */
@Service
public class ObjectQueryCatalog {

    public static final String QUERIES_ROOT = "root.platform.queries";

    private final ObjectManager objectManager;

    public ObjectQueryCatalog(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    @Transactional
    public void ensureCatalog() {
        SystemObjectCatalogSupport.ensureFolder(objectManager, QUERIES_ROOT, ObjectType.QUERIES, null);
    }
}
