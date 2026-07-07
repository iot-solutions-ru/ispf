package com.ispf.server.tenant;

import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class TenantService {

    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,62}$");

    private final TenantStore tenantStore;
    private final ObjectManager objectManager;
    private final TenantQuotaService tenantQuotaService;
    private final TenantScopeService tenantScopeService;

    public TenantService(
            TenantStore tenantStore,
            ObjectManager objectManager,
            TenantQuotaService tenantQuotaService,
            TenantScopeService tenantScopeService
    ) {
        this.tenantStore = tenantStore;
        this.objectManager = objectManager;
        this.tenantQuotaService = tenantQuotaService;
        this.tenantScopeService = tenantScopeService;
    }

    public List<Tenant> listTenants() {
        return tenantStore.listAll();
    }

    @Transactional
    public Tenant createTenant(TenantDraft draft) {
        validateTenantId(draft.tenantId());
        if (tenantStore.findById(draft.tenantId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant exists: " + draft.tenantId());
        }
        if (objectManager.tree().findByPath(TenantPaths.tenantRoot(draft.tenantId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant path exists: " + draft.tenantId());
        }
        ensureTenantsRoot();
        Tenant tenant = tenantStore.insert(draft);
        bootstrapTenantTree(tenant);
        return tenant;
    }

    @Transactional
    public void deleteTenant(String tenantId) {
        Tenant tenant = tenantStore.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        objectManager.delete(tenant.objectPath());
        tenantStore.delete(tenantId);
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
