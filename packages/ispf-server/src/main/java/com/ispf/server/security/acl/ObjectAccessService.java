package com.ispf.server.security.acl;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.security.RoleScopeAccessService;
import com.ispf.server.tenant.TenantScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ObjectAccessService {

    /**
     * Empty ACL is fail-open for solution trees, but fail-closed for sensitive platform
     * branches (global admin always bypasses earlier). Operators/developers need an explicit grant.
     */
    private static final List<String> FAIL_CLOSED_EMPTY_ACL_PREFIXES = List.of(
            "root.platform.security",
            "root.platform.tenants"
    );

    private final ObjectAclStore aclStore;
    private final RoleScopeAccessService roleScopeAccessService;
    private final TenantScopeService tenantScopeService;

    public ObjectAccessService(
            ObjectAclStore aclStore,
            RoleScopeAccessService roleScopeAccessService,
            TenantScopeService tenantScopeService
    ) {
        this.aclStore = aclStore;
        this.roleScopeAccessService = roleScopeAccessService;
        this.tenantScopeService = tenantScopeService;
    }

    public List<ObjectAclStore.ObjectAclEntry> listEntries(String objectPath) {
        return aclStore.listByPath(objectPath);
    }

    public void replaceEntries(String objectPath, List<ObjectAclStore.ObjectAclEntryDraft> drafts) {
        for (ObjectAclStore.ObjectAclEntryDraft draft : drafts) {
            validateDraft(draft);
        }
        aclStore.replaceEntries(objectPath, drafts);
    }

    public void requireRead(String objectPath, Authentication authentication) {
        if (!canRead(objectPath, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Read access denied for " + objectPath);
        }
    }

    public void requireWrite(String objectPath, Authentication authentication) {
        if (!canWrite(objectPath, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Write access denied for " + objectPath);
        }
    }

    public void requireAdmin(Authentication authentication) {
        if (!IspfRoles.isGlobalAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    public void requireConfigurator(Authentication authentication) {
        if (!isConfigurator(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Configurator access required");
        }
    }

    public boolean isConfigurator(Authentication authentication) {
        return IspfRoles.isConfigurator(authentication);
    }

    public void requireGrantAcl(String objectPath, Authentication authentication) {
        if (IspfRoles.isGlobalAdmin(authentication) || canGrant(objectPath, authentication)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACL grant denied for " + objectPath);
    }

    public void requireInvoke(String objectPath, Authentication authentication) {
        if (!canInvoke(objectPath, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invoke access denied for " + objectPath);
        }
    }

    public void requireVariableRead(
            String objectPath,
            String variableName,
            List<String> readRoles,
            Authentication authentication
    ) {
        if (!canVariableRead(objectPath, variableName, readRoles, authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Read access denied for variable " + variableName + " on " + objectPath
            );
        }
    }

    public void requireVariableWrite(
            String objectPath,
            String variableName,
            List<String> writeRoles,
            Authentication authentication
    ) {
        if (!canVariableWrite(objectPath, variableName, writeRoles, authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Write access denied for variable " + variableName + " on " + objectPath
            );
        }
    }

    /**
     * Per-event / per-function invoke roles (BL-154). Empty list = object INVOKE ACL only.
     */
    public void requireMemberInvoke(
            String objectPath,
            String memberKind,
            String memberName,
            List<String> invokeRoles,
            Authentication authentication
    ) {
        if (!canInvoke(objectPath, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invoke access denied for " + objectPath);
        }
        if (!hasVariableRole(objectPath, invokeRoles, authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Invoke access denied for " + memberKind + " " + memberName + " on " + objectPath
            );
        }
    }

    public boolean canVariableRead(
            String objectPath,
            String variableName,
            List<String> readRoles,
            Authentication authentication
    ) {
        if (!canRead(objectPath, authentication)) {
            return false;
        }
        return hasVariableRole(objectPath, readRoles, authentication);
    }

    public boolean canVariableWrite(
            String objectPath,
            String variableName,
            List<String> writeRoles,
            Authentication authentication
    ) {
        if (!canWrite(objectPath, authentication)) {
            return false;
        }
        return hasVariableRole(objectPath, writeRoles, authentication);
    }

    private boolean hasVariableRole(
            String objectPath,
            List<String> requiredRoles,
            Authentication authentication
    ) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }
        if (IspfRoles.isGlobalAdmin(authentication) || isTenantOwnerOf(objectPath, authentication)) {
            return true;
        }
        Set<String> roles = IspfRoles.extractRoles(authentication);
        for (String required : requiredRoles) {
            if (required != null && roles.contains(required)) {
                return true;
            }
        }
        return false;
    }

    public boolean canRead(String objectPath, Authentication authentication) {
        return hasPermission(objectPath, "READ", authentication);
    }

    public boolean canWrite(String objectPath, Authentication authentication) {
        return hasPermission(objectPath, "WRITE", authentication);
    }

    public boolean canInvoke(String objectPath, Authentication authentication) {
        return hasPermission(objectPath, "INVOKE", authentication);
    }

    public boolean canGrant(String objectPath, Authentication authentication) {
        return IspfRoles.isGlobalAdmin(authentication)
                || isTenantOwnerOf(objectPath, authentication)
                || hasPermission(objectPath, "OWNER", authentication);
    }

    private boolean hasPermission(String objectPath, String permission, Authentication authentication) {
        if (IspfRoles.isGlobalAdmin(authentication)) {
            return true;
        }
        // tenant-admin: OWNER-level within their tenant branch only (never global admin).
        if (isTenantOwnerOf(objectPath, authentication)) {
            return true;
        }
        if (!roleScopeAccessService.isPathInRoleScope(objectPath, authentication)) {
            return false;
        }
        List<ObjectAclStore.ObjectAclEntry> entries = aclStore.findEffectiveEntries(objectPath);
        if (entries.isEmpty()) {
            // Fail-open for normal solution paths; fail-closed for sensitive platform trees.
            return !isFailClosedEmptyAclPath(objectPath);
        }
        Set<String> roles = IspfRoles.extractRoles(authentication);
        String username = authentication != null ? authentication.getName() : null;
        for (ObjectAclStore.ObjectAclEntry entry : entries) {
            if (!matchesPermission(entry.permission(), permission)) {
                continue;
            }
            if ("ROLE".equalsIgnoreCase(entry.principalType()) && roles.contains(entry.principalId())) {
                return true;
            }
            if ("USER".equalsIgnoreCase(entry.principalType())
                    && username != null
                    && username.equalsIgnoreCase(entry.principalId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTenantOwnerOf(String objectPath, Authentication authentication) {
        if (!IspfRoles.isTenantAdmin(authentication) || objectPath == null || objectPath.isBlank()) {
            return false;
        }
        Optional<String> tenantRoot = tenantScopeService.tenantRootPrefix(authentication);
        if (tenantRoot.isEmpty()) {
            return false;
        }
        String prefix = tenantRoot.get();
        return objectPath.equals(prefix) || objectPath.startsWith(prefix + ".");
    }

    private static boolean matchesPermission(String granted, String required) {
        if (granted == null || required == null) {
            return false;
        }
        String normalizedGranted = granted.toUpperCase();
        String normalizedRequired = required.toUpperCase();
        if (normalizedGranted.equals(normalizedRequired)) {
            return true;
        }
        return switch (normalizedGranted) {
            case "OWNER" -> Set.of("READ", "WRITE", "INVOKE", "EDITOR", "VIEWER", "OWNER").contains(normalizedRequired);
            case "EDITOR" -> Set.of("READ", "WRITE", "INVOKE", "EDITOR", "VIEWER").contains(normalizedRequired);
            case "VIEWER" -> Set.of("READ", "VIEWER").contains(normalizedRequired);
            default -> false;
        };
    }

    static boolean isFailClosedEmptyAclPath(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return false;
        }
        for (String prefix : FAIL_CLOSED_EMPTY_ACL_PREFIXES) {
            if (objectPath.equals(prefix) || objectPath.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    private static void validateDraft(ObjectAclStore.ObjectAclEntryDraft draft) {
        if (!Set.of("ROLE", "USER").contains(draft.principalType().toUpperCase())) {
            throw new IllegalArgumentException("principalType must be ROLE or USER");
        }
        if (!Set.of("READ", "WRITE", "INVOKE", "OWNER", "EDITOR", "VIEWER")
                .contains(draft.permission().toUpperCase())) {
            throw new IllegalArgumentException(
                    "permission must be READ, WRITE, INVOKE, OWNER, EDITOR, or VIEWER"
            );
        }
        if (draft.principalId() == null || draft.principalId().isBlank()) {
            throw new IllegalArgumentException("principalId is required");
        }
    }
}
