package com.ispf.server.security;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.config.IspfRoles;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.tenant.TenantScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PlatformRoleService {

    public static final String OPERATOR_READONLY = "operator-readonly";
    public static final String MES_SUPERVISOR = "mes-supervisor";

    private final PlatformRoleStore roleStore;
    private final PlatformUserStore userStore;
    private final PlatformUserObjectTreeService objectTreeService;
    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;
    private final TenantScopeService tenantScopeService;

    public PlatformRoleService(
            PlatformRoleStore roleStore,
            PlatformUserStore userStore,
            PlatformUserObjectTreeService objectTreeService,
            ObjectManager objectManager,
            ObjectMapper objectMapper,
            TenantScopeService tenantScopeService
    ) {
        this.roleStore = roleStore;
        this.userStore = userStore;
        this.objectTreeService = objectTreeService;
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
        this.tenantScopeService = tenantScopeService;
    }

    @Transactional
    public void ensureDefaultRoles() {
        if (!roleStore.exists()) {
            upsertBuiltIn(IspfRoles.ADMIN, "Full platform administration");
            upsertBuiltIn(IspfRoles.TENANT_ADMIN, "Tenant local owner — full control of root.tenant.{id}.*");
            upsertBuiltIn(IspfRoles.DEVELOPER, "Solution development — objects, apps, SQL tools");
            upsertBuiltIn(IspfRoles.OPERATOR, "Operator HMI and read-only automation");
        } else {
            upsertBuiltIn(IspfRoles.TENANT_ADMIN, "Tenant local owner — full control of root.tenant.{id}.*");
            upsertBuiltIn(IspfRoles.DEVELOPER, "Solution development — objects, apps, SQL tools");
        }
        ensureRoleTemplates();
        objectTreeService.syncRoles();
    }

    private void ensureRoleTemplates() {
        upsertTemplate(
                OPERATOR_READONLY,
                "Operator (read-only)",
                "Read-only operator — HMI, trends, and events without write access to variables or objects."
        );
        upsertTemplate(
                MES_SUPERVISOR,
                "MES Supervisor",
                "MES supervisor — OEE dashboards, work-queue oversight, and ISA-95 scoped read access."
        );
    }

    @Transactional
    public Map<String, Object> createRole(String name, String displayName, String description) {
        return createRole(name, displayName, description, null);
    }

    @Transactional
    public Map<String, Object> createRole(
            String name,
            String displayName,
            String description,
            Authentication authentication
    ) {
        String normalized = normalizeRoleName(name);
        if (roleStore.findByName(normalized).isPresent()) {
            throw new IllegalArgumentException("Role already exists: " + normalized);
        }
        if (IspfRoles.ADMIN.equals(normalized)) {
            throw new IllegalArgumentException("Cannot create global admin role");
        }
        String tenantId = null;
        if (authentication != null
                && IspfRoles.isTenantAdmin(authentication)
                && !IspfRoles.isGlobalAdmin(authentication)) {
            tenantId = requireCallerTenantId(authentication);
        } else if (authentication != null && !IspfRoles.isGlobalAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role create requires admin or tenant-admin");
        }
        PlatformRoleStore.PlatformRole role = new PlatformRoleStore.PlatformRole(
                normalized,
                displayName != null && !displayName.isBlank() ? displayName : normalized,
                description != null ? description : "",
                false,
                tenantId,
                Instant.now(),
                Instant.now()
        );
        roleStore.upsert(role);
        objectTreeService.syncRole(role);
        return toSummary(role);
    }

    @Transactional
    public Map<String, Object> updateRole(String name, String displayName, String description) {
        return updateRole(name, displayName, description, null);
    }

    @Transactional
    public Map<String, Object> updateRole(
            String name,
            String displayName,
            String description,
            Authentication authentication
    ) {
        PlatformRoleStore.PlatformRole existing = roleStore.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
        requireRoleWritable(existing, authentication);
        String resolvedDisplayName = displayName != null && !displayName.isBlank()
                ? displayName
                : existing.displayName();
        String resolvedDescription = description != null ? description : existing.description();
        roleStore.updateProfile(name, resolvedDisplayName, resolvedDescription);
        PlatformRoleStore.PlatformRole updated = roleStore.findByName(name).orElseThrow();
        objectTreeService.syncRole(updated);
        return toSummary(updated);
    }

    @Transactional
    public void deleteRole(String name) {
        deleteRole(name, null);
    }

    @Transactional
    public void deleteRole(String name, Authentication authentication) {
        PlatformRoleStore.PlatformRole role = roleStore.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
        if (role.builtIn()) {
            throw new IllegalArgumentException("Cannot delete built-in role: " + name);
        }
        requireRoleWritable(role, authentication);
        if (isRoleAssigned(name)) {
            throw new IllegalArgumentException("Role is assigned to users: " + name);
        }
        roleStore.delete(name);
        if (objectManager.tree().findByPath(role.objectPath()).isPresent()) {
            objectManager.delete(role.objectPath());
        }
    }

    public List<Map<String, Object>> listRoles() {
        return listRoles(null);
    }

    public List<Map<String, Object>> listRoles(Authentication authentication) {
        if (authentication != null
                && IspfRoles.isTenantAdmin(authentication)
                && !IspfRoles.isGlobalAdmin(authentication)) {
            String tenantId = requireCallerTenantId(authentication);
            return roleStore.listBuiltInsAndTenant(tenantId).stream().map(this::toSummary).toList();
        }
        return roleStore.listAll().stream().map(this::toSummary).toList();
    }

    public void validateRoleNames(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        for (String role : roles) {
            if (roleStore.findByName(role).isEmpty()) {
                throw new IllegalArgumentException("Unknown role: " + role);
            }
        }
    }

    public void validateAssignableRoles(List<String> roles, Authentication authentication) {
        validateRoleNames(roles);
        boolean tenantAdminCaller = IspfRoles.isTenantAdmin(authentication)
                && !IspfRoles.isGlobalAdmin(authentication);
        if (!tenantAdminCaller) {
            return;
        }
        String callerTenant = requireCallerTenantId(authentication);
        for (String role : roles) {
            if (IspfRoles.ADMIN.equals(role)) {
                throw new IllegalArgumentException("Cannot assign global admin role from tenant scope");
            }
            PlatformRoleStore.PlatformRole stored = roleStore.findByName(role).orElseThrow();
            if (stored.tenantId() != null
                    && !stored.tenantId().isBlank()
                    && !callerTenant.equals(stored.tenantId())) {
                throw new IllegalArgumentException("Role belongs to another tenant: " + role);
            }
        }
    }

    public boolean isSecurityRolePath(String path) {
        return path != null && (
                path.startsWith(PlatformUserService.ROLES_PATH_PREFIX)
                        || path.contains(".platform.security.roles.")
        );
    }

    public String roleNameFromPath(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private void requireRoleWritable(PlatformRoleStore.PlatformRole role, Authentication authentication) {
        if (authentication == null || IspfRoles.isGlobalAdmin(authentication)) {
            return;
        }
        if (!IspfRoles.isTenantAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role write requires admin or tenant-admin");
        }
        if (role.builtIn()) {
            throw new IllegalArgumentException("Cannot modify built-in role: " + role.name());
        }
        String callerTenant = requireCallerTenantId(authentication);
        if (role.tenantId() == null || !callerTenant.equals(role.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role belongs to another tenant");
        }
    }

    private String requireCallerTenantId(Authentication authentication) {
        return tenantScopeService.resolveTenantId(authentication)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Tenant admin requires tenant_id assignment"
                ));
    }

    private boolean isRoleAssigned(String roleName) {
        return userStore.listAll().stream()
                .anyMatch(user -> deserializeRoles(user.rolesJson()).contains(roleName));
    }

    private List<String> deserializeRoles(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void upsertBuiltIn(String name, String description) {
        Optional<PlatformRoleStore.PlatformRole> existing = roleStore.findByName(name);
        if (existing.isPresent()) {
            roleStore.updateProfile(name, name, description);
            return;
        }
        roleStore.upsert(new PlatformRoleStore.PlatformRole(
                name,
                name,
                description,
                true,
                null,
                Instant.now(),
                Instant.now()
        ));
    }

    private void upsertTemplate(String name, String displayName, String description) {
        if (roleStore.findByName(name).isPresent()) {
            roleStore.updateProfile(name, displayName, description);
            return;
        }
        roleStore.upsert(new PlatformRoleStore.PlatformRole(
                name,
                displayName,
                description,
                false,
                null,
                Instant.now(),
                Instant.now()
        ));
    }

    private static String normalizeRoleName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name is required");
        }
        String normalized = name.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9._-]{2,64}")) {
            throw new IllegalArgumentException("Invalid role name format");
        }
        return normalized;
    }

    private Map<String, Object> toSummary(PlatformRoleStore.PlatformRole role) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", role.name());
        summary.put("displayName", role.displayName());
        summary.put("description", role.description());
        summary.put("builtIn", role.builtIn());
        summary.put("template", isRoleTemplate(role.name()));
        summary.put("tenantId", role.tenantId());
        summary.put("objectPath", role.objectPath());
        summary.put("createdAt", role.createdAt().toString());
        summary.put("updatedAt", role.updatedAt().toString());
        return summary;
    }

    private static boolean isRoleTemplate(String name) {
        return OPERATOR_READONLY.equals(name) || MES_SUPERVISOR.equals(name);
    }
}
