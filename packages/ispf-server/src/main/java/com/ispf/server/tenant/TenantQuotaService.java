package com.ispf.server.tenant;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantQuotaService {

    private final TenantStore tenantStore;
    private final ObjectManager objectManager;

    public TenantQuotaService(TenantStore tenantStore, ObjectManager objectManager) {
        this.tenantStore = tenantStore;
        this.objectManager = objectManager;
    }

    public TenantUsage usage(String tenantId) {
        String prefix = TenantPaths.tenantPlatform(tenantId) + ".";
        int devices = 0;
        int objects = 0;
        for (PlatformObject node : objectManager.tree().all()) {
            String path = node.path();
            if (!path.startsWith(prefix)) {
                continue;
            }
            objects++;
            if (node.type() == ObjectType.DEVICE) {
                devices++;
            }
        }
        return new TenantUsage(tenantId, devices, objects);
    }

    public void assertCanCreate(String parentPath, ObjectType type) {
        String tenantId = tenantIdForPath(parentPath);
        if (tenantId == null) {
            return;
        }
        Tenant tenant = tenantStore.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        TenantUsage current = usage(tenantId);
        if (tenant.maxObjects() != null && current.objects() >= tenant.maxObjects()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tenant object quota exceeded: " + tenant.maxObjects()
            );
        }
        if (type == ObjectType.DEVICE && tenant.maxDevices() != null && current.devices() >= tenant.maxDevices()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tenant device quota exceeded: " + tenant.maxDevices()
            );
        }
    }

    public static String tenantIdForPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String prefix = TenantPaths.TENANTS_ROOT + ".";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String remainder = path.substring(prefix.length());
        int dot = remainder.indexOf('.');
        if (dot <= 0) {
            return remainder.isBlank() ? null : remainder;
        }
        return remainder.substring(0, dot);
    }

    public record TenantUsage(String tenantId, int devices, int objects) {
    }
}
