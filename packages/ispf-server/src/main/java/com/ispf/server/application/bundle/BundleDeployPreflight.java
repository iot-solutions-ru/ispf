package com.ispf.server.application.bundle;

import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleBlueprint;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleMigration;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleObject;
import com.ispf.server.application.data.ApplicationSchemaSupport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Checks a bundle's object tree and migration scripts without writing the live object tree or SQL.
 * A parent counts as present when it already exists or an earlier object in this manifest creates it.
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
        List<String> errors = new ArrayList<>();
        Set<String> planned = new LinkedHashSet<>();
        seedSingletonPaths(blueprints, pathExists, planned);
        dryRunObjects(objects, pathExists, planned, errors);
        dryRunMigrations(migrations, tablePrefix, errors);
        return List.copyOf(errors);
    }

    private static void seedSingletonPaths(
            List<BundleBlueprint> blueprints,
            Predicate<String> pathExists,
            Set<String> planned
    ) {
        if (blueprints == null) {
            return;
        }
        for (BundleBlueprint blueprint : blueprints) {
            if (blueprint == null || blueprint.type() != BlueprintType.SINGLETON) {
                continue;
            }
            if (blueprint.name() == null || blueprint.name().isBlank()) {
                continue;
            }
            String path = singletonInstancePath(blueprint);
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
            if (!planned.add(path)) {
                errors.add("dryRun:object:" + name + ": duplicate path " + path);
            }
        }
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
