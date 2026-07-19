package com.ispf.server.security;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.tenant.TenantPaths;
import com.ispf.server.tenant.TenantScopeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Enforces ISA-95 / role-template {@code scopePathPrefixes} on REST object access (BL-157).
 * When the principal has one or more template roles with scopes, object paths must match the union.
 * Global admin is unrestricted. Tenant-admin is unrestricted within their tenant root only.
 * Tenant-scoped users get {@code root.platform.*} template prefixes remapped under their tenant platform.
 */
@Service
public class RoleScopeAccessService {

    private final TenantScopeService tenantScopeService;

    public RoleScopeAccessService(TenantScopeService tenantScopeService) {
        this.tenantScopeService = tenantScopeService;
    }

    public boolean isPathInRoleScope(String objectPath, Authentication authentication) {
        if (objectPath == null || objectPath.isBlank()) {
            return false;
        }
        if (IspfRoles.isGlobalAdmin(authentication)) {
            return true;
        }
        Optional<String> tenantRoot = tenantScopeService.tenantRootPrefix(authentication);
        if (IspfRoles.isTenantAdmin(authentication)) {
            return tenantRoot
                    .map(prefix -> pathMatchesPrefix(objectPath, prefix)
                            || "root".equals(objectPath)
                            || TenantPaths.TENANTS_ROOT.equals(objectPath))
                    .orElse(false);
        }
        if (IspfRoles.isDeveloper(authentication) && tenantRoot.isEmpty()) {
            return true;
        }
        Set<String> prefixes = resolveScopePrefixes(authentication);
        if (prefixes.isEmpty()) {
            // Non-template roles: still constrain developers/operators with tenant_id to their branch.
            return tenantRoot
                    .map(prefix -> pathMatchesPrefix(objectPath, prefix)
                            || "root".equals(objectPath)
                            || TenantPaths.TENANTS_ROOT.equals(objectPath))
                    .orElse(true);
        }
        for (String prefix : prefixes) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            if (pathMatchesPrefix(objectPath, prefix)
                    || prefix.startsWith(objectPath + ".")
                    || "root".equals(objectPath)
                    || "root.platform".equals(objectPath)
                    || TenantPaths.TENANTS_ROOT.equals(objectPath)) {
                return true;
            }
            if (tenantRoot.isPresent()) {
                String tenantPlatform = TenantPaths.tenantPlatform(
                        tenantRoot.get().substring(TenantPaths.TENANTS_ROOT.length() + 1)
                );
                if (objectPath.equals(tenantPlatform) || objectPath.startsWith(tenantPlatform + ".")) {
                    // Allow walking the tenant platform root even when templates list leaf folders only.
                    if (prefix.startsWith(tenantPlatform + ".") || prefix.equals(tenantPlatform)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Set<String> resolveScopePrefixes(Authentication authentication) {
        Set<String> roles = IspfRoles.extractRoles(authentication);
        Set<String> prefixes = new LinkedHashSet<>();
        Optional<String> tenantId = tenantScopeService.resolveTenantId(authentication);
        for (String role : roles) {
            PlatformRoleTemplatePermissions.scopePathPrefixes(role).ifPresent(list -> {
                for (String prefix : list) {
                    if (tenantId.isPresent()) {
                        prefixes.add(TenantPaths.remapPlatformPrefix(prefix, tenantId.get()));
                    } else {
                        prefixes.add(prefix);
                    }
                }
            });
        }
        return prefixes;
    }

    private static boolean pathMatchesPrefix(String objectPath, String prefix) {
        return objectPath.equals(prefix) || objectPath.startsWith(prefix + ".");
    }
}
