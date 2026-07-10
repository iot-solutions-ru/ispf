package com.ispf.server.bootstrap;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.federation.FederationPaths;
import com.ispf.server.process.ProcessProgramPaths;
import com.ispf.server.security.PlatformUserService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds the platform catalog skeleton on first start. Demo objects are seeded by {@link DemoFixtureBootstrap}.
 * Default role templates ({@code operator-readonly}, {@code mes-supervisor}) are seeded via
 * {@link com.ispf.server.security.PlatformRoleService#ensureDefaultRoles()} on application ready.
 */
@Component
public class PlatformBootstrap {

    public PlatformBootstrap() {
    }

    public void initialize(ObjectTree tree) {
        initializeCatalog(tree);
    }

    private void initializeCatalog(ObjectTree tree) {
        register(tree, PlatformCatalogSortOrder.PLATFORM_ROOT, ObjectType.PLATFORM, null);
        register(tree, "root.tenant", ObjectType.TENANT, null);

        register(tree, PlatformUserService.SECURITY_ROOT, ObjectType.SECURITY, "security-folder-v1");
        register(tree, "root.platform.devices", ObjectType.DEVICES, null);
        register(tree, "root.platform.alert-rules", ObjectType.ALERT_RULES, null);
        register(tree, "root.platform.operator-apps", ObjectType.OPERATOR_APPS, "app-folder-v1");
        register(tree, "root.platform.dashboards", ObjectType.DASHBOARDS, null);
        register(tree, "root.platform.mimics", ObjectType.MIMICS, null);

        registerCatalogFolder(tree, "root.platform.relative-blueprints");
        registerCatalogFolder(tree, "root.platform.absolute-blueprints");
        registerCatalogFolder(tree, "root.platform.instance-types");
        register(tree, "root.platform.reports", ObjectType.REPORTS, null);
        register(tree, "root.platform.correlators", ObjectType.CORRELATORS, null);
        register(tree, "root.platform.workflows", ObjectType.WORKFLOWS, null);
        register(tree, "root.platform.queries", ObjectType.QUERIES, null);
        register(tree, "root.platform.event-filters", ObjectType.EVENT_FILTERS, null);
        register(tree, ProcessProgramPaths.PROCESS_PROGRAMS_ROOT, ObjectType.PROCESS_PROGRAMS, null);
        register(tree, "root.platform.mes", ObjectType.MES, null);

        register(tree, "root.platform.applications", ObjectType.APPLICATIONS, "app-folder-v1");
        registerCatalogFolder(tree, "root.platform.instances");
        register(tree, FederationPaths.FEDERATION_ROOT, ObjectType.AGENT, null);
    }

    private static void register(ObjectTree tree, String path, ObjectType type, String templateId) {
        if (tree.findByPath(path).isPresent()) {
            return;
        }
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(path)
                .orElseThrow(() -> new IllegalStateException("Missing system description: " + path));
        int sortOrder = PlatformCatalogSortOrder.forPath(path)
                .orElseThrow(() -> new IllegalStateException("Missing default sort order: " + path));
        tree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                path,
                type,
                entry.displayName(),
                entry.description(),
                templateId,
                sortOrder
        ));
    }

    private static void registerCatalogFolder(ObjectTree tree, String path) {
        if (tree.findByPath(path).isPresent()) {
            return;
        }
        SystemObjectDescriptions.Entry entry = SystemObjectDescriptions.resolve(path)
                .orElseThrow(() -> new IllegalStateException("Missing system description: " + path));
        int sortOrder = PlatformCatalogSortOrder.forPath(path)
                .orElseThrow(() -> new IllegalStateException("Missing default sort order: " + path));
        tree.register(new PlatformObject(
                UUID.randomUUID().toString(),
                path,
                ObjectType.BLUEPRINT,
                entry.displayName(),
                entry.description(),
                null,
                sortOrder
        ));
    }
}
