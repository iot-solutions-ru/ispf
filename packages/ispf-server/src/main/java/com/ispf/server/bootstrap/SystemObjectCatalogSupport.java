package com.ispf.server.bootstrap;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;

public final class SystemObjectCatalogSupport {

    private SystemObjectCatalogSupport() {
    }

    public static void ensureFolder(ObjectManager objectManager, String path, ObjectType type, String templateId) {
        objectManager.ensureSystemCatalogFolder(path, type, templateId);
    }
}
