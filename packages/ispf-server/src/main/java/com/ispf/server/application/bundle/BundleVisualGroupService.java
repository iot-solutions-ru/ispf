package com.ispf.server.application.bundle;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.VisualGroupMember;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.tree.ApplicationObjectTreeService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.migration.MigrationObjectService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.VisualGroupService;
import com.ispf.server.operator.OperatorAppObjectTreeService;
import com.ispf.server.report.ReportService;
import com.ispf.server.schedule.ScheduleObjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maintains one visual group per deployed bundle inside each relevant system catalog
 * (devices, dashboards, reports, …): {@code root.platform.{catalog}.{appId}}.
 */
@Service
public class BundleVisualGroupService {

    static final List<String> BUNDLE_CATALOG_ROOTS = List.of(
            "root.platform.devices",
            "root.platform.dashboards",
            "root.platform.reports",
            "root.platform.workflows",
            "root.platform.alert-rules",
            "root.platform.correlators",
            ScheduleObjectService.SCHEDULES_ROOT,
            DataSourcePathResolver.DATA_SOURCES_ROOT,
            SqlBindingObjectService.BINDINGS_ROOT,
            MigrationObjectService.MIGRATIONS_ROOT,
            ApplicationObjectTreeService.APPLICATIONS_ROOT,
            OperatorAppObjectTreeService.OPERATOR_APPS_ROOT
    );

    private static final String GROUP_NODE_PREFIX = "bundle-";

    private final ObjectManager objectManager;
    private final VisualGroupService visualGroupService;
    private final ApplicationDataStore dataStore;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ObjectMapper objectMapper;

    public BundleVisualGroupService(
            ObjectManager objectManager,
            VisualGroupService visualGroupService,
            ApplicationDataStore dataStore,
            ApplicationBundleSnapshotStore snapshotStore,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.visualGroupService = visualGroupService;
        this.dataStore = dataStore;
        this.snapshotStore = snapshotStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncAllRegisteredBundles() {
        for (Map<String, Object> app : dataStore.listAllApps()) {
            String appId = String.valueOf(app.get("app_id"));
            snapshotStore.findActive(appId).ifPresent(snapshot -> {
                try {
                    ApplicationBundleDeployService.BundleManifest manifest = objectMapper.readValue(
                            snapshot.manifestJson(),
                            ApplicationBundleDeployService.BundleManifest.class
                    );
                    String displayName = manifest.displayName() != null && !manifest.displayName().isBlank()
                            ? manifest.displayName()
                            : appId;
                    syncBundle(appId, displayName, manifest);
                } catch (Exception ignored) {
                    // skip broken snapshots during startup reconciliation
                }
            });
        }
    }

    @Transactional
    public List<String> syncBundle(
            String appId,
            String displayName,
            ApplicationBundleDeployService.BundleManifest manifest
    ) {
        Map<String, List<String>> membersByCatalog = collectMembersByCatalog(appId, manifest);
        List<String> syncedGroupPaths = new ArrayList<>();
        String nodeName = groupNodeName(appId);
        String title = displayName != null && !displayName.isBlank() ? displayName : appId;

        for (String catalogRoot : BUNDLE_CATALOG_ROOTS) {
            String groupPath = groupPathForCatalogAndApp(catalogRoot, appId);
            List<String> memberPaths = membersByCatalog.getOrDefault(catalogRoot, List.of());
            if (memberPaths.isEmpty()) {
                removeBundleGroupIfPresent(catalogRoot, nodeName);
                continue;
            }
            ensureBundleVisualGroup(catalogRoot, nodeName, groupPath, title, appId);
            List<VisualGroupMember> members = new ArrayList<>();
            int order = 0;
            for (String path : memberPaths) {
                if (path.equals(groupPath) || !objectManager.tree().findByPath(path).isPresent()) {
                    continue;
                }
                members.add(new VisualGroupMember(path, order++));
            }
            if (members.isEmpty()) {
                removeBundleGroupIfPresent(catalogRoot, nodeName);
                continue;
            }
            visualGroupService.setMembers(groupPath, members);
            syncedGroupPaths.add(groupPath);
        }
        return syncedGroupPaths;
    }

    public static String groupPathForCatalogAndApp(String catalogRoot, String appId) {
        return catalogRoot + "." + groupNodeName(appId);
    }

    public static String groupNodeName(String appId) {
        return GROUP_NODE_PREFIX + ApplicationObjectTreeService.sanitizeNodeName(appId);
    }

    /** Tree paths owned by a bundle deploy, excluding the application folder node (kept on remove). */
    public static List<String> managedRemovalPaths(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest
    ) {
        Set<String> paths = new LinkedHashSet<>();
        collectMembersByCatalog(appId, manifest).values().forEach(memberPaths -> paths.addAll(memberPaths));
        paths.remove(ApplicationBundleDeployService.applicationTreePath(appId));
        for (String catalogRoot : BUNDLE_CATALOG_ROOTS) {
            paths.add(groupPathForCatalogAndApp(catalogRoot, appId));
        }
        return paths.stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();
    }

    @Transactional
    public void removeAllBundleGroups(String appId) {
        String nodeName = groupNodeName(appId);
        for (String catalogRoot : BUNDLE_CATALOG_ROOTS) {
            removeBundleGroupIfPresent(catalogRoot, nodeName);
        }
    }

    static Map<String, List<String>> collectMembersByCatalog(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest
    ) {
        Map<String, Set<String>> grouped = new LinkedHashMap<>();
        java.util.function.Consumer<String> add = path -> {
            if (path == null || path.isBlank()) {
                return;
            }
            String catalogRoot = catalogRootForMemberPath(path);
            if (catalogRoot == null) {
                return;
            }
            grouped.computeIfAbsent(catalogRoot, ignored -> new LinkedHashSet<>()).add(path);
        };

        add.accept(DataSourcePathResolver.DATA_SOURCES_ROOT + "."
                + DataSourcePathResolver.sanitizeNodeName(appId));

        if (manifest.objects() != null) {
            for (ApplicationBundleDeployService.BundleObject object : manifest.objects()) {
                if (object.parentPath() == null || object.name() == null) {
                    continue;
                }
                add.accept(resolveChildPath(object.parentPath(), object.name()));
            }
        }
        if (manifest.dashboards() != null) {
            for (ApplicationBundleDeployService.BundleDashboard dashboard : manifest.dashboards()) {
                add.accept(dashboard.path());
            }
        }
        if (manifest.workflows() != null) {
            for (ApplicationBundleDeployService.BundleWorkflow workflow : manifest.workflows()) {
                add.accept(workflow.path());
            }
        }
        if (manifest.reports() != null) {
            for (ApplicationBundleDeployService.BundleReport report : manifest.reports()) {
                if (report.reportId() != null && !report.reportId().isBlank()) {
                    add.accept(ReportService.reportPath(report.reportId()));
                }
            }
        }
        if (manifest.alertRules() != null) {
            for (ApplicationBundleDeployService.BundleAlertRule rule : manifest.alertRules()) {
                if (rule.name() != null && !rule.name().isBlank()) {
                    add.accept(AutomationTreeService.rulePathForName(rule.name()));
                }
            }
        }
        if (manifest.correlators() != null) {
            for (ApplicationBundleDeployService.BundleCorrelator correlator : manifest.correlators()) {
                if (correlator.name() != null && !correlator.name().isBlank()) {
                    add.accept(AutomationTreeService.correlatorPathForName(correlator.name()));
                }
            }
        }
        if (manifest.schedules() != null) {
            for (ApplicationBundleDeployService.BundleSchedule schedule : manifest.schedules()) {
                if (schedule.scheduleId() != null && !schedule.scheduleId().isBlank()) {
                    add.accept(ScheduleObjectService.SCHEDULES_ROOT + "."
                            + ApplicationObjectTreeService.sanitizeNodeName(schedule.scheduleId()));
                }
            }
        }
        if (manifest.bindings() != null) {
            for (ApplicationBundleDeployService.BundleSqlBinding binding : manifest.bindings()) {
                if (binding.objectPath() == null || binding.variable() == null) {
                    continue;
                }
                String bindingId = binding.objectPath().replace('.', '-') + "-" + binding.variable();
                add.accept(SqlBindingObjectService.BINDINGS_ROOT + "."
                        + SqlBindingObjectService.sanitizeNodeName(bindingId));
            }
        }
        if (manifest.migrations() != null) {
            for (ApplicationBundleDeployService.BundleMigration migration : manifest.migrations()) {
                if (migration.id() != null && !migration.id().isBlank()) {
                    add.accept(MigrationObjectService.MIGRATIONS_ROOT + "."
                            + ApplicationObjectTreeService.sanitizeNodeName(migration.id()));
                }
            }
        }
        if (ApplicationBundleDeployService.hasOperatorUiManifest(manifest)) {
            add.accept(ApplicationBundleDeployService.operatorAppTreePath(appId));
        }
        add.accept(ApplicationBundleDeployService.applicationTreePath(appId));

        Map<String, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((catalog, paths) -> result.put(catalog, List.copyOf(paths)));
        return result;
    }

    static String catalogRootForMemberPath(String path) {
        if (path == null || !path.startsWith("root.platform.")) {
            return null;
        }
        for (String catalogRoot : BUNDLE_CATALOG_ROOTS) {
            if (path.equals(catalogRoot) || path.startsWith(catalogRoot + ".")) {
                return catalogRoot;
            }
        }
        return null;
    }

    private void removeBundleGroupIfPresent(String catalogRoot, String nodeName) {
        String groupPath = catalogRoot + "." + nodeName;
        objectManager.tree().findByPath(groupPath)
                .filter(node -> node.type() == ObjectType.VISUAL_GROUP)
                .ifPresent(node -> objectManager.delete(groupPath));
    }

    private void ensureBundleVisualGroup(
            String catalogRoot,
            String nodeName,
            String groupPath,
            String displayName,
            String appId
    ) {
        if (objectManager.tree().findByPath(groupPath).isPresent()) {
            PlatformObject existing = objectManager.require(groupPath);
            if (existing.type() != ObjectType.VISUAL_GROUP) {
                objectManager.reconcileType(groupPath, ObjectType.VISUAL_GROUP);
            }
            objectManager.updateInfo(
                    groupPath,
                    displayName,
                    bundleGroupDescription(appId, catalogRoot)
            );
            return;
        }
        objectManager.create(
                catalogRoot,
                nodeName,
                ObjectType.VISUAL_GROUP,
                displayName,
                bundleGroupDescription(appId, catalogRoot),
                null
        );
    }

    private static String bundleGroupDescription(String appId, String catalogRoot) {
        return "Bundle " + appId + " objects in " + catalogRoot.substring(catalogRoot.lastIndexOf('.') + 1);
    }

    private static String resolveChildPath(String parentPath, String name) {
        if (parentPath == null || parentPath.isBlank()) {
            return name;
        }
        return parentPath + "." + name;
    }
}
