package com.ispf.server.api.support;

import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Shared pre/post-conditions for object-tree write endpoints: tenant scope, per-object ACL,
 * edit lease and collaboration context. Extracted from {@code ObjectController} so the
 * controllers split out of it (leases, functions/events, …) enforce the same rules.
 */
@Component
public class ObjectWriteGuard {

    private final TenantScopeService tenantScopeService;
    private final ObjectAccessService objectAccessService;
    private final ObjectEditLeaseService editLeaseService;
    private final TenantVirtualRootService tenantVirtualRootService;

    public ObjectWriteGuard(
            TenantScopeService tenantScopeService,
            ObjectAccessService objectAccessService,
            ObjectEditLeaseService editLeaseService,
            TenantVirtualRootService tenantVirtualRootService
    ) {
        this.tenantScopeService = tenantScopeService;
        this.objectAccessService = objectAccessService;
        this.editLeaseService = editLeaseService;
        this.tenantVirtualRootService = tenantVirtualRootService;
    }

    /** Tenant virtual-root path → canonical tree path. */
    public String canonicalPath(String path, Authentication authentication) {
        return tenantVirtualRootService.toCanonical(path, authentication);
    }

    /** Scope + ACL + lease checks, then binds the collaboration write context. Pair with {@link #endWrite()}. */
    public void beginWrite(String path, Authentication authentication, HttpHeaders headers) {
        tenantScopeService.requirePathInScope(path, authentication);
        objectAccessService.requireWrite(path, authentication);
        editLeaseService.assertWritable(path, authentication != null ? authentication.getName() : "system");
        ObjectCollaborationSupport.bindWriteContext(authentication, headers);
    }

    public void endWrite() {
        ObjectCollaborationSupport.clearContext();
    }
}
