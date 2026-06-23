package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.federation.FederationProxyMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rules for structural tree operations (create child, reorder siblings).
 */
public final class ObjectTreePolicy {

    private ObjectTreePolicy() {
    }

    public static void assertParentAllowsStructuralChildren(PlatformObject parent) {
        if (FederationProxyMetadata.isProxy(parent)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot add children under federation-bound object: " + parent.path()
            );
        }
        if (parent.type() == ObjectType.VISUAL_GROUP) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot add structural children under visual group: " + parent.path()
                    + ". Use group-members API instead."
            );
        }
    }

    public static void assertParentAllowsStructuralChildren(String parentPath, ObjectManager objectManager) {
        objectManager.tree().findByPath(parentPath).ifPresent(ObjectTreePolicy::assertParentAllowsStructuralChildren);
    }
}
