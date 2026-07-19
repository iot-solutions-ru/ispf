package com.ispf.server.tenant;

import com.ispf.core.object.ObjectType;
import com.ispf.server.config.IspfRoles;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.acl.ObjectAclStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class TenantService {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,62}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TenantStore tenantStore;
    private final ObjectManager objectManager;
    private final TenantQuotaService tenantQuotaService;
    private final TenantScopeService tenantScopeService;
    private final TenantSchemaService tenantSchemaService;
    private final PlatformUserService platformUserService;
    private final ObjectAclStore objectAclStore;

    public TenantService(
            TenantStore tenantStore,
            ObjectManager objectManager,
            TenantQuotaService tenantQuotaService,
            TenantScopeService tenantScopeService,
            TenantSchemaService tenantSchemaService,
            PlatformUserService platformUserService,
            ObjectAclStore objectAclStore
    ) {
        this.tenantStore = tenantStore;
        this.objectManager = objectManager;
        this.tenantQuotaService = tenantQuotaService;
        this.tenantScopeService = tenantScopeService;
        this.tenantSchemaService = tenantSchemaService;
        this.platformUserService = platformUserService;
        this.objectAclStore = objectAclStore;
    }

    public List<Tenant> listTenants() {
        return tenantStore.listAll();
    }

    public Tenant getTenant(String tenantId) {
        return tenantStore.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    @Transactional
    public TenantCreateResult createTenant(TenantDraft draft) {
        validateTenantId(draft.tenantId());
        if (tenantStore.findById(draft.tenantId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant exists: " + draft.tenantId());
        }
        if (objectManager.tree().findByPath(TenantPaths.tenantRoot(draft.tenantId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant path exists: " + draft.tenantId());
        }
        ensureTenantsRoot();
        Tenant tenant = tenantStore.insert(draft);
        tenantSchemaService.provisionTenantSchema(tenant.tenantId());
        bootstrapTenantTree(tenant);
        String adminUsername = resolveAdminUsername(draft);
        String adminPassword = resolveAdminPassword(draft);
        String adminDisplayName = draft.adminDisplayName() != null && !draft.adminDisplayName().isBlank()
                ? draft.adminDisplayName().trim()
                : tenant.displayName() + " Admin";
        platformUserService.createUser(
                adminUsername,
                adminDisplayName,
                adminPassword,
                List.of(IspfRoles.TENANT_ADMIN),
                tenant.tenantId(),
                null
        );
        // Parent ACL is inherited by the whole branch. Include built-in tenant roles so
        // operators/developers keep access; tenant-admin also has OWNER-level bypass in ObjectAccessService.
        objectAclStore.replaceEntries(
                TenantPaths.tenantRoot(tenant.tenantId()),
                List.of(
                        new ObjectAclStore.ObjectAclEntryDraft("USER", adminUsername, "OWNER"),
                        new ObjectAclStore.ObjectAclEntryDraft("ROLE", IspfRoles.TENANT_ADMIN, "OWNER"),
                        new ObjectAclStore.ObjectAclEntryDraft("ROLE", IspfRoles.DEVELOPER, "EDITOR"),
                        new ObjectAclStore.ObjectAclEntryDraft("ROLE", IspfRoles.OPERATOR, "EDITOR")
                )
        );
        return new TenantCreateResult(tenant, adminUsername, adminPassword);
    }

    @Transactional
    public void deleteTenant(String tenantId) {
        Tenant tenant = tenantStore.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        objectManager.delete(tenant.objectPath());
        tenantStore.delete(tenantId);
        tenantSchemaService.dropTenantSchema(tenantId);
    }

    @Transactional
    public void assignUserToTenant(String username, String tenantId) {
        tenantStore.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        try {
            tenantStore.assignUserTenant(username, tenantId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
        tenantScopeService.invalidateUserCache(username);
    }

    @Transactional
    public Tenant updateQuotas(String tenantId, TenantQuotas quotas) {
        tenantStore.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantStore.updateQuotas(tenantId, quotas);
        return tenantStore.findById(tenantId).orElseThrow();
    }

    public TenantQuotaService.TenantUsage usage(String tenantId) {
        tenantStore.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        return tenantQuotaService.usage(tenantId);
    }

    @Transactional
    public void clearUserTenant(String username) {
        tenantStore.clearUserTenant(username);
        tenantScopeService.invalidateUserCache(username);
    }

    public void ensureTenantsRoot() {
        if (objectManager.tree().findByPath(TenantPaths.TENANTS_ROOT).isPresent()) {
            return;
        }
        objectManager.create(
                "root",
                "tenant",
                ObjectType.TENANT,
                "Tenants",
                "Multi-tenant namespaces (root.tenant.{id}.platform.*)",
                null
        );
    }

    private void bootstrapTenantTree(Tenant tenant) {
        objectManager.create(
                TenantPaths.TENANTS_ROOT,
                tenant.tenantId(),
                ObjectType.TENANT,
                tenant.displayName(),
                "Tenant namespace for " + tenant.tenantId(),
                null
        );
        objectManager.create(
                TenantPaths.tenantRoot(tenant.tenantId()),
                "platform",
                ObjectType.PLATFORM,
                tenant.displayName() + " Platform",
                "Tenant-scoped platform tree",
                null
        );
        objectManager.create(
                TenantPaths.tenantPlatform(tenant.tenantId()),
                "devices",
                ObjectType.DEVICES,
                "Devices",
                "Tenant devices",
                null
        );
        objectManager.create(
                TenantPaths.tenantPlatform(tenant.tenantId()),
                "dashboards",
                ObjectType.DASHBOARDS,
                "Dashboards",
                "Tenant dashboards",
                null
        );
        objectManager.create(
                TenantPaths.tenantPlatform(tenant.tenantId()),
                "security",
                ObjectType.SECURITY,
                "Security",
                "Tenant-local users and roles",
                null
        );
        objectManager.create(
                TenantPaths.tenantSecurity(tenant.tenantId()),
                "users",
                ObjectType.USERS,
                "Users",
                "Tenant users",
                null
        );
        objectManager.create(
                TenantPaths.tenantSecurity(tenant.tenantId()),
                "roles",
                ObjectType.ROLES,
                "Roles",
                "Tenant custom roles",
                null
        );
    }

    private static String resolveAdminUsername(TenantDraft draft) {
        if (draft.adminUsername() != null && !draft.adminUsername().isBlank()) {
            return draft.adminUsername().trim().toLowerCase();
        }
        return draft.tenantId() + "-admin";
    }

    private static String resolveAdminPassword(TenantDraft draft) {
        if (draft.adminPassword() != null && !draft.adminPassword().isBlank()) {
            return draft.adminPassword();
        }
        return generatePassword();
    }

    private static String generatePassword() {
        String alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    static void validateTenantId(String tenantId) {
        if (tenantId == null || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "tenantId must match [a-z][a-z0-9-]{1,62}"
            );
        }
    }
}
