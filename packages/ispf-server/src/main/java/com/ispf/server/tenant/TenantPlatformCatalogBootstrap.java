package com.ispf.server.tenant;

import com.ispf.core.object.ObjectType;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.bootstrap.SystemObjectDescriptions;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.eventfilter.EventFilterObjectService;
import com.ispf.server.migration.MigrationObjectService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.process.ProcessProgramPaths;
import com.ispf.server.query.ObjectQueryCatalog;
import com.ispf.server.schedule.ScheduleObjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the core platform catalog under {@code root.tenant.{id}.platform.*}.
 * <p>
 * Mirrors {@link com.ispf.server.bootstrap.PlatformBootstrap} base folders (not marketplace MES,
 * not federation — tenants install solutions themselves; federation stays global-admin).
 */
@Component
public class TenantPlatformCatalogBootstrap {

    private static final Logger log = LoggerFactory.getLogger(TenantPlatformCatalogBootstrap.class);

    /**
     * Leaf folder under {@code root.platform} → type / optional template.
     * Descriptions are taken from {@link SystemObjectDescriptions} for the global path.
     */
    private static final List<CatalogFolder> CORE_FOLDERS = List.of(
            new CatalogFolder("security", ObjectType.SECURITY, "security-folder-v1"),
            new CatalogFolder("devices", ObjectType.DEVICES, null),
            new CatalogFolder("alert-rules", ObjectType.ALERT_RULES, null),
            new CatalogFolder("operator-apps", ObjectType.OPERATOR_APPS, "app-folder-v1"),
            new CatalogFolder("dashboards", ObjectType.DASHBOARDS, null),
            new CatalogFolder("mimics", ObjectType.MIMICS, null),
            new CatalogFolder("relative-blueprints", ObjectType.BLUEPRINT, null),
            new CatalogFolder("absolute-blueprints", ObjectType.BLUEPRINT, null),
            new CatalogFolder("instance-types", ObjectType.BLUEPRINT, null),
            new CatalogFolder("reports", ObjectType.REPORTS, null),
            new CatalogFolder("correlators", ObjectType.CORRELATORS, null),
            new CatalogFolder("workflows", ObjectType.WORKFLOWS, null),
            new CatalogFolder(leaf(ObjectQueryCatalog.QUERIES_ROOT), ObjectType.QUERIES, null),
            new CatalogFolder(leaf(EventFilterObjectService.EVENT_FILTERS_ROOT), ObjectType.EVENT_FILTERS, null),
            new CatalogFolder(leaf(ProcessProgramPaths.PROCESS_PROGRAMS_ROOT), ObjectType.PROCESS_PROGRAMS, null),
            new CatalogFolder(leaf(ScheduleObjectService.SCHEDULES_ROOT), ObjectType.SCHEDULES, null),
            new CatalogFolder(leaf(DataSourcePathResolver.DATA_SOURCES_ROOT), ObjectType.DATA_SOURCES, null),
            new CatalogFolder(leaf(SqlBindingObjectService.BINDINGS_ROOT), ObjectType.BINDINGS, null),
            new CatalogFolder(leaf(MigrationObjectService.MIGRATIONS_ROOT), ObjectType.MIGRATIONS, null),
            new CatalogFolder("applications", ObjectType.APPLICATIONS, "app-folder-v1"),
            new CatalogFolder("instances", ObjectType.BLUEPRINT, null)
    );

    private final ObjectManager objectManager;
    private final TenantStore tenantStore;

    public TenantPlatformCatalogBootstrap(ObjectManager objectManager, TenantStore tenantStore) {
        this.objectManager = objectManager;
        this.tenantStore = tenantStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillExistingTenants() {
        for (Tenant tenant : tenantStore.listAll()) {
            try {
                ensureTenantPlatformCatalog(tenant.tenantId(), tenant.displayName());
            } catch (RuntimeException ex) {
                log.warn("Tenant platform catalog backfill failed for {}: {}", tenant.tenantId(), ex.getMessage());
            }
        }
    }

    /**
     * Idempotent: creates tenant root + platform + core folders (+ security users/roles).
     */
    @Transactional
    public void ensureTenantPlatformCatalog(String tenantId, String displayName) {
        String label = displayName != null && !displayName.isBlank() ? displayName.trim() : tenantId;
        ensureTenantsRoot();
        ensureChild(
                TenantPaths.TENANTS_ROOT,
                tenantId,
                ObjectType.TENANT,
                label,
                "Tenant namespace for " + tenantId,
                null
        );
        ensureChild(
                TenantPaths.tenantRoot(tenantId),
                "platform",
                ObjectType.PLATFORM,
                label + " Platform",
                "Tenant-scoped platform tree",
                null
        );
        String platform = TenantPaths.tenantPlatform(tenantId);
        for (CatalogFolder folder : CORE_FOLDERS) {
            SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve("root.platform." + folder.leaf())
                    .orElse(new SystemObjectDescriptions.Entry(titleCase(folder.leaf()), "Tenant " + folder.leaf()));
            ensureChild(platform, folder.leaf(), folder.type(), entry.displayName(), entry.description(), folder.templateId());
        }
        ensureChild(
                TenantPaths.tenantSecurity(tenantId),
                "users",
                ObjectType.USERS,
                "Users",
                "Tenant users",
                null
        );
        ensureChild(
                TenantPaths.tenantSecurity(tenantId),
                "roles",
                ObjectType.ROLES,
                "Roles",
                "Tenant custom roles",
                null
        );
    }

    private void ensureTenantsRoot() {
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

    private void ensureChild(
            String parentPath,
            String name,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    ) {
        String path = parentPath + "." + name;
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        objectManager.create(parentPath, name, type, displayName, description, templateId);
    }

    private static String leaf(String globalPath) {
        int dot = globalPath.lastIndexOf('.');
        return dot >= 0 ? globalPath.substring(dot + 1) : globalPath;
    }

    private static String titleCase(String leaf) {
        if (leaf == null || leaf.isBlank()) {
            return leaf;
        }
        String[] parts = leaf.replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private record CatalogFolder(String leaf, ObjectType type, String templateId) {
    }
}
