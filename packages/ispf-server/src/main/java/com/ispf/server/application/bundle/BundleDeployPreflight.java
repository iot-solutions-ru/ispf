package com.ispf.server.application.bundle;

import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleBlueprint;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleDashboard;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleManifest;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleMigration;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleObject;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleWorkflow;
import com.ispf.server.application.data.ApplicationSchemaSupport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Checks a bundle's object tree and migration scripts without writing the live object tree or SQL.
 * A parent counts as present when it already exists, registration will create it, or an earlier
 * object in this manifest creates it.
 */
public final class BundleDeployPreflight {

    private BundleDeployPreflight() {
    }

    public static List<String> dryRun(
            List<BundleObject> objects,
            List<BundleBlueprint> blueprints,
            List<BundleMigration> migrations,
            String tablePrefix,
            Predicate<String> pathExists
    ) {
        return dryRun(
                objects,
                blueprints,
                List.of(),
                List.of(),
                migrations,
                tablePrefix,
                List.of(),
                pathExists
        );
    }

    public static List<String> dryRun(
            BundleManifest manifest,
            String applicationPath,
            String dataSourcePath,
            Predicate<String> pathExists
    ) {
        List<String> registrationPaths = new ArrayList<>(2);
        if (applicationPath != null && !applicationPath.isBlank()) {
            registrationPaths.add(applicationPath);
        }
        if (dataSourcePath != null && !dataSourcePath.isBlank()) {
            registrationPaths.add(dataSourcePath);
        }
        return dryRun(
                manifest.objects(),
                manifest.blueprints(),
                manifest.dashboards(),
                manifest.workflows(),
                manifest.migrations(),
                manifest.tablePrefix(),
                registrationPaths,
                pathExists
        );
    }

    public static List<String> dryRun(
            List<BundleObject> objects,
            List<BundleBlueprint> blueprints,
            List<BundleDashboard> dashboards,
            List<BundleWorkflow> workflows,
            List<BundleMigration> migrations,
            String tablePrefix,
            List<String> registrationPaths,
            Predicate<String> pathExists
    ) {
        List<String> errors = new ArrayList<>();
        Set<String> planned = new LinkedHashSet<>();
        seedRegistrationPaths(registrationPaths, pathExists, planned);
        seedSingletonPaths(blueprints, pathExists, planned, errors);
        dryRunObjects(objects, pathExists, planned, errors);
        dryRunDashboards(dashboards, pathExists, planned, errors);
        dryRunWorkflows(workflows, pathExists, planned, errors);
        dryRunMigrations(migrations, tablePrefix, errors);
        return List.copyOf(errors);
    }

    private static void seedRegistrationPaths(
            List<String> registrationPaths,
            Predicate<String> pathExists,
            Set<String> planned
    ) {
        if (registrationPaths == null || registrationPaths.isEmpty()) {
            return;
        }
        for (String rawPath : registrationPaths) {
            if (rawPath == null || rawPath.isBlank()) {
                continue;
            }
            String path = rawPath.trim();
            int dot = path.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String parent = path.substring(0, dot);
            if (planned.contains(parent) || pathExists.test(parent)) {
                planned.add(path);
            }
        }
    }

    private static void seedSingletonPaths(
            List<BundleBlueprint> blueprints,
            Predicate<String> pathExists,
            Set<String> planned,
            List<String> errors
    ) {
        if (blueprints == null) {
            return;
        }
        if (pathExists.test("root.platform")) {
            planned.add(BlueprintCatalogRoots.SINGLETON);
            planned.add(BlueprintCatalogRoots.INSTANCES);
        }
        for (BundleBlueprint blueprint : blueprints) {
            if (blueprint == null || blueprint.type() != BlueprintType.SINGLETON) {
                continue;
            }
            if (blueprint.name() == null || blueprint.name().isBlank()) {
                errors.add("dryRun:blueprint: SINGLETON name is required");
                continue;
            }
            String name = blueprint.name().trim();
            String path = singletonInstancePath(blueprint);
            int dot = path.lastIndexOf('.');
            if (dot <= 0) {
                errors.add("dryRun:blueprint:" + name + ": invalid singletonInstancePath: " + path);
                continue;
            }
            String parent = path.substring(0, dot);
            if (planned.contains(parent) || pathExists.test(parent)) {
                planned.add(path);
            } else {
                errors.add("dryRun:blueprint:" + name + ": singletonInstancePath parent does not exist: " + parent);
            }
        }
    }

    private static String singletonInstancePath(BundleBlueprint blueprint) {
        if (blueprint.parameters() != null) {
            String configured = blueprint.parameters().get("singletonInstancePath");
            if (configured != null && !configured.isBlank()) {
                return configured.trim();
            }
        }
        return BlueprintCatalogRoots.SINGLETON + "." + blueprint.name().trim();
    }

    private static void dryRunObjects(
            List<BundleObject> objects,
            Predicate<String> pathExists,
            Set<String> planned,
            List<String> errors
    ) {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        Set<String> seenInObjects = new LinkedHashSet<>();
        for (BundleObject object : objects) {
            if (object == null) {
                errors.add("dryRun:object: entry is null");
                continue;
            }
            String name = object.name() == null ? "" : object.name().trim();
            if (name.isBlank()) {
                errors.add("dryRun:object: name is required");
                continue;
            }
            if (name.indexOf('.') >= 0) {
                errors.add("dryRun:object:" + name + ": child name must not contain '.'");
                continue;
            }
            if (object.parentPath() == null || object.parentPath().isBlank()) {
                errors.add("dryRun:object:" + name + ": parentPath is required");
                continue;
            }
            if (object.type() == null || object.type().isBlank()) {
                errors.add("dryRun:object:" + name + ": type is required");
                continue;
            }
            try {
                ObjectType.valueOf(object.type());
            } catch (IllegalArgumentException ex) {
                errors.add("dryRun:object:" + name + ": unknown type " + object.type());
                continue;
            }
            String parent = object.parentPath();
            boolean parentReady = planned.contains(parent) || pathExists.test(parent);
            if (!parentReady) {
                errors.add("dryRun:object:" + name + ": parent does not exist: " + parent);
                continue;
            }
            String path = parent + "." + name;
            if (!seenInObjects.add(path)) {
                errors.add("dryRun:object:" + name + ": duplicate path " + path);
                continue;
            }
            planned.add(path);
        }
    }

    private static void dryRunDashboards(
            List<BundleDashboard> dashboards,
            Predicate<String> pathExists,
            Set<String> planned,
            List<String> errors
    ) {
        if (dashboards == null || dashboards.isEmpty()) {
            return;
        }
        for (BundleDashboard dashboard : dashboards) {
            if (dashboard == null) {
                errors.add("dryRun:dashboard: entry is null");
                continue;
            }
            dryRunLeafPath("dashboard", dashboard.path(), pathExists, planned, errors);
        }
    }

    private static void dryRunWorkflows(
            List<BundleWorkflow> workflows,
            Predicate<String> pathExists,
            Set<String> planned,
            List<String> errors
    ) {
        if (workflows == null || workflows.isEmpty()) {
            return;
        }
        for (BundleWorkflow workflow : workflows) {
            if (workflow == null) {
                errors.add("dryRun:workflow: entry is null");
                continue;
            }
            dryRunLeafPath("workflow", workflow.path(), pathExists, planned, errors);
        }
    }

    private static void dryRunLeafPath(
            String kind,
            String rawPath,
            Predicate<String> pathExists,
            Set<String> planned,
            List<String> errors
    ) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isBlank()) {
            errors.add("dryRun:" + kind + ": path is required");
            return;
        }
        if (planned.contains(path) || pathExists.test(path)) {
            planned.add(path);
            return;
        }
        int dot = path.lastIndexOf('.');
        if (dot <= 0 || dot == path.length() - 1) {
            errors.add("dryRun:" + kind + ":" + path + ": invalid object path");
            return;
        }
        String parent = path.substring(0, dot);
        if (!planned.contains(parent) && !pathExists.test(parent)) {
            errors.add("dryRun:" + kind + ":" + path + ": parent does not exist: " + parent);
            return;
        }
        planned.add(path);
    }

    private static void dryRunMigrations(
            List<BundleMigration> migrations,
            String tablePrefix,
            List<String> errors
    ) {
        if (migrations == null || migrations.isEmpty()) {
            return;
        }
        String prefix = tablePrefix != null ? tablePrefix : "";
        Set<String> seenIds = new LinkedHashSet<>();
        for (BundleMigration migration : migrations) {
            if (migration == null) {
                errors.add("dryRun:migration: entry is null");
                continue;
            }
            if (migration.id() == null || migration.id().isBlank()) {
                errors.add("dryRun:migration: id is required");
                continue;
            }
            if (!seenIds.add(migration.id())) {
                errors.add("dryRun:migration:" + migration.id() + ": duplicate id");
                continue;
            }
            if (migration.sql() == null || migration.sql().isBlank()) {
                errors.add("dryRun:migration:" + migration.id() + ": sql is required");
                continue;
            }
            try {
                ApplicationSchemaSupport.validateMigrationSql(migration.sql(), prefix);
            } catch (RuntimeException ex) {
                String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                errors.add("dryRun:migration:" + migration.id() + ": " + message);
            }
        }
    }
}
