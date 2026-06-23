package com.ispf.server.object;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.security.PlatformRoleService;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.automation.AutomationTreeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ObjectBulkDeleteService {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "root",
            "root.platform",
            "root.tenant"
    );

    private final ObjectManager objectManager;
    private final PlatformUserService platformUserService;
    private final PlatformRoleService platformRoleService;
    private final AutomationTreeService automationTreeService;

    public ObjectBulkDeleteService(
            ObjectManager objectManager,
            PlatformUserService platformUserService,
            PlatformRoleService platformRoleService,
            AutomationTreeService automationTreeService
    ) {
        this.objectManager = objectManager;
        this.platformUserService = platformUserService;
        this.platformRoleService = platformRoleService;
        this.automationTreeService = automationTreeService;
    }

    public boolean isProtectedPath(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        if (PROTECTED_PATHS.contains(path)) {
            return true;
        }
        return path.startsWith("root.platform.")
                && objectManager.tree().findByPath(path)
                .map(node -> isProtectedCatalogFolder(node))
                .orElse(false);
    }

    @Transactional
    public BulkDeleteResult deleteAll(List<String> paths) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String path : paths) {
            if (path != null && !path.isBlank()) {
                unique.add(path.trim());
            }
        }
        List<BulkDeleteResult.Entry> results = new ArrayList<>();
        int deleted = 0;
        for (String path : unique) {
            try {
                if (isProtectedPath(path)) {
                    results.add(BulkDeleteResult.Entry.failed(path, "Protected system path"));
                    continue;
                }
                deleteOne(path);
                results.add(BulkDeleteResult.Entry.ok(path));
                deleted++;
            } catch (Exception e) {
                results.add(BulkDeleteResult.Entry.failed(path, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
        return new BulkDeleteResult(deleted, results);
    }

    private void deleteOne(String path) {
        if (platformUserService.isSecurityUserPath(path)) {
            platformUserService.deleteUser(platformUserService.usernameFromPath(path));
            return;
        }
        if (platformRoleService.isSecurityRolePath(path)) {
            platformRoleService.deleteRole(platformRoleService.roleNameFromPath(path));
            return;
        }
        if (objectManager.tree().findByPath(path)
                .filter(node -> node.type() == ObjectType.CORRELATOR)
                .isPresent()) {
            automationTreeService.deleteCorrelator(path);
            return;
        }
        objectManager.delete(path);
    }

    private static boolean isProtectedCatalogFolder(PlatformObject node) {
        return switch (node.type()) {
            case ROOT, TENANT, PLATFORM, DEVICES, DASHBOARDS, WORKFLOWS, ALERT_RULES, CORRELATORS,
                 APPLICATIONS, OPERATOR_APPS, SECURITY, USERS, ROLES, DATA_SOURCES, REPORTS,
                 FUNCTIONS, SCHEDULES, BINDINGS, MIGRATIONS, SCREENS -> true;
            default -> false;
        };
    }

    public record BulkDeleteResult(int deleted, List<Entry> results) {
        public record Entry(String path, boolean success, String error) {
            static Entry ok(String path) {
                return new Entry(path, true, null);
            }

            static Entry failed(String path, String error) {
                return new Entry(path, false, error);
            }
        }
    }
}
