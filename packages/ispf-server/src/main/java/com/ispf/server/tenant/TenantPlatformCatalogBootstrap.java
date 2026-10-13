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
import com.ispf.server.spi.ObjectTreeReadyOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
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
            new CatalogFolder("mixin-blueprints", ObjectType.BLUEPRINT, null),
            new CatalogFolder("singleton-blueprints", ObjectType.BLUEPRINT, null),
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
    private final TransactionTemplate tenantTransaction;

    /** Empty when every existing tenant was catalogued. There is no switch that turns this contour off. */
    private volatile String backfillFailure = "";

    public TenantPlatformCatalogBootstrap(
            ObjectManager objectManager,
            TenantStore tenantStore,
            PlatformTransactionManager transactionManager
    ) {
        this.objectManager = objectManager;
        this.tenantStore = tenantStore;
        this.tenantTransaction = new TransactionTemplate(transactionManager);
        this.tenantTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * One transaction per tenant. A failure is recorded for health and readiness and is not thrown:
     * this listener shares {@link ObjectTreeReadyOrder#AFTER_OBJECT_TREE_READY_ORDER} with driver
     * auto-start and the binding index, so an exception would skip the remaining listeners.
     * {@code markObjectTreeReady} is one step earlier ({@code Integer.MAX_VALUE - 1}).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(ObjectTreeReadyOrder.AFTER_OBJECT_TREE_READY_ORDER)
    public void backfillExistingTenants() {
        List<Tenant> tenants;
        try {
            tenants = tenantStore.listAll();
        } catch (RuntimeException ex) {
            backfillFailure = failureText(ex);
            log.error(
                    "Tenant platform catalog backfill failed, readiness is down, startup continues: {}",
                    backfillFailure
            );
            return;
        }
        List<String> failed = new ArrayList<>();
        for (Tenant tenant : tenants) {
            try {
                tenantTransaction.executeWithoutResult(status ->
                        ensureTenantPlatformCatalog(tenant.tenantId(), tenant.displayName()));
            } catch (RuntimeException ex) {
                String detail = tenant.tenantId() + ": " + failureText(ex);
                failed.add(detail);
                log.error("Tenant platform catalog backfill failed for {}", detail);
            }
        }
        if (failed.isEmpty()) {
            backfillFailure = "";
            log.info("Tenant platform catalog backfill ready ({} tenants)", tenants.size());
            return;
        }
        backfillFailure = String.join("; ", failed);
        log.error(
                "Tenant platform catalog backfill incomplete, readiness is down, startup continues: {}",
                backfillFailure
        );
    }

    public boolean isContourReady() {
        return backfillFailure.isEmpty();
    }

    public String backfillFailure() {
        return backfillFailure;
    }

    private static String failureText(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
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
