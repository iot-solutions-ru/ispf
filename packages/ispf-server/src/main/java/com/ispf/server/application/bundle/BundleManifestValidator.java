package com.ispf.server.application.bundle;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintCatalogRoots;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.application.script.FunctionScriptValidator;
import com.ispf.server.license.CommercialBundleLicenseVerifier;
import com.ispf.server.license.CommercialLicenseException;
import com.ispf.server.object.ObjectManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class BundleManifestValidator {

    private static final Set<String> KNOWN_WIDGET_TYPES = Set.of(
            "value", "indicator", "toggle", "chart", "sparkline", "progress", "gauge", "status-badge",
            "function", "function-form", "pie-chart", "history-table", "variable-editor",
            "svg-widget", "composite-widget", "dashboard-link", "event-feed",
            "object-table", "work-queue", "card-grid", "report",
            "sub-dashboard", "panel", "tab-panel", "map",
            "label", "image", "html-snippet", "object-tree", "breadcrumbs", "timer", "context-list",
            "linear-gauge", "input-form", "drawer-panel", "carousel", "steps-panel",
            "gantt-chart", "network-graph", "spreadsheet", "liquid-gauge", "nav-menu",
            "scada-mimic"
    );

    private final FunctionScriptValidator scriptValidator;
    private final BundleDependencyVerifier dependencyVerifier;
    private final CommercialBundleLicenseVerifier licenseVerifier;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ObjectManager> objectManagerProvider;
    private final ObjectProvider<BundleSchemaValidator> schemaValidatorProvider;

    public BundleManifestValidator(
            FunctionScriptValidator scriptValidator,
            BundleDependencyVerifier dependencyVerifier,
            CommercialBundleLicenseVerifier licenseVerifier,
            ApplicationBundleSnapshotStore snapshotStore,
            ObjectMapper objectMapper,
            ObjectProvider<ObjectManager> objectManagerProvider,
            ObjectProvider<BundleSchemaValidator> schemaValidatorProvider
    ) {
        this.scriptValidator = scriptValidator;
        this.dependencyVerifier = dependencyVerifier;
        this.licenseVerifier = licenseVerifier;
        this.snapshotStore = snapshotStore;
        this.objectMapper = objectMapper;
        this.objectManagerProvider = objectManagerProvider;
        this.schemaValidatorProvider = schemaValidatorProvider;
    }

    public BundleValidationResult validate(String appId, ApplicationBundleDeployService.BundleManifest manifest) {
        return validate(appId, manifest, false);
    }

    public BundleValidationResult dryRun(String appId, ApplicationBundleDeployService.BundleManifest manifest) {
        return validate(appId, manifest, true);
    }

    private BundleValidationResult validate(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest,
            boolean dryRun
    ) {
        BundleValidationResult.Builder builder = BundleValidationResult.builder();

        BundleSchemaValidator schemaValidator = schemaValidatorProvider.getIfAvailable();
        if (schemaValidator != null) {
            schemaValidator.validate(manifest, builder);
        }

        if (appId == null || appId.isBlank()) {
            builder.addError("appId is required");
        }
        if (manifest.version() == null || manifest.version().isBlank()) {
            builder.addError("manifest.version is required");
        } else if (!BundleSemverSupport.isValid(manifest.version())) {
            builder.addError("manifest.version must be semver MAJOR.MINOR.PATCH (e.g. \"1.0.0\")");
        }
        snapshotStore.findActive(appId).ifPresent(active ->
                BundleSemverSupport.majorBumpWarning(active.bundleVersion(), manifest.version())
                        .ifPresent(builder::addWarning)
        );
        if (manifest.displayName() == null || manifest.displayName().isBlank()) {
            builder.addError("manifest.displayName is required");
        }
        if (manifest.schemaName() == null || manifest.schemaName().isBlank()) {
            builder.addError("manifest.schemaName is required");
        }
        validateMinimumContent(manifest, builder);

        String tablePrefix = manifest.tablePrefix() != null ? manifest.tablePrefix() : "";
        validateMigrations(manifest, tablePrefix, builder);
        validateFunctions(manifest, builder);
        validateBindings(manifest, builder);
        validateEvents(manifest, builder);
        validateTests(manifest, builder);
        validateDashboards(manifest, builder);
        validateObjects(manifest, builder);
        validateLogicHosts(manifest, builder);
        validateReports(manifest, builder);
        validateDependencies(appId, manifest, builder);
        validateLicense(appId, manifest, builder);

        if (dryRun) {
            collectWouldApply(manifest, builder);
        }

        return builder.build();
    }

    private void validateMinimumContent(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (hasNonEmptyList(manifest.migrations())
                || hasNonEmptyList(manifest.functions())
                || hasNonEmptyList(manifest.dashboards())
                || hasNonEmptyList(manifest.objects())
                || hasNonEmptyList(manifest.workflows())
                || hasNonEmptyList(manifest.blueprints())
                || hasNonEmptyList(manifest.bindings())
                || hasNonEmptyList(manifest.reports())
                || hasNonEmptyList(manifest.alertRules())
                || hasNonEmptyList(manifest.correlators())
                || hasNonEmptyList(manifest.schedules())
                || hasNonEmptyList(manifest.events())
                || hasNonEmptyMap(manifest.operatorUi())
                || hasNonEmptyMap(manifest.operatorManifest())) {
            return;
        }
        builder.addError(
                "manifest must include at least one deployable section "
                        + "(migrations, functions, dashboards, operatorUi, objects, workflows, blueprints, reports, events, ...)"
        );
    }

    private static boolean hasNonEmptyList(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private static boolean hasNonEmptyMap(Map<?, ?> values) {
        return values != null && !values.isEmpty();
    }

    private void validateMigrations(
            ApplicationBundleDeployService.BundleManifest manifest,
            String tablePrefix,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.migrations() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleMigration migration : manifest.migrations()) {
            if (migration.id() == null || migration.id().isBlank()) {
                builder.addError("migration: id is required (use {id, sql} objects, not declarative column schemas)");
                continue;
            }
            if (migration.sql() == null || migration.sql().isBlank()) {
                builder.addError("migration " + migration.id() + ": sql is required");
                continue;
            }
            try {
                ApplicationSchemaSupport.validateMigrationSql(migration.sql(), tablePrefix);
            } catch (Exception ex) {
                builder.addError("migration " + migration.id() + ": " + ex.getMessage());
            }
        }
    }

    private void validateFunctions(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.functions() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleFunction function : manifest.functions()) {
            String label = function.functionName() != null ? function.functionName() : function.objectPath();
            if (function.objectPath() == null || function.objectPath().isBlank()) {
                builder.addError("function " + label + ": objectPath is required");
            }
            if (function.functionName() == null || function.functionName().isBlank()) {
                builder.addError("function at " + function.objectPath() + ": functionName is required");
            }
            if (function.source() == null || function.source().body() == null || function.source().body().isBlank()) {
                builder.addError("function " + label + ": source.body is required");
                continue;
            }
            try {
                scriptValidator.validate(function.source().body());
            } catch (Exception ex) {
                builder.addError("function " + label + ": " + ex.getMessage());
            }
        }
    }

    private void validateEvents(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.events() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleEvent event : manifest.events()) {
            if (event.id() == null || event.id().isBlank()) {
                builder.addError("events[]: id is required");
            }
            if (event.payloadSchema() != null) {
                try {
                    objectMapper.writeValueAsString(event.payloadSchema());
                } catch (Exception ex) {
                    builder.addError("event " + event.id() + ": payloadSchema must be JSON-serializable");
                }
            }
        }
    }

    private void validateTests(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.tests() == null) {
            return;
        }
        int index = 0;
        for (ApplicationBundleDeployService.BundleTest test : manifest.tests()) {
            String path = "tests[" + index + "]";
            if (test.id() == null || test.id().isBlank()) {
                builder.addError(path + ": id is required");
            }
            String kind = test.kind() != null ? test.kind().trim().toLowerCase(java.util.Locale.ROOT) : "";
            if (!"function".equals(kind) && !"telemetry".equals(kind)) {
                builder.addError(path + ": kind must be function or telemetry");
            }
            if ("function".equals(kind)
                    && (isBlank(test.objectPath()) || isBlank(test.functionName()))) {
                builder.addError(path + ": function tests require objectPath and functionName");
            }
            if ("telemetry".equals(kind)
                    && (isBlank(test.objectPath()) || isBlank(test.variable()))) {
                builder.addError(path + ": telemetry tests require objectPath and variable");
            }
            index++;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void validateBindings(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.bindings() == null) {
            return;
        }
        int index = 0;
        for (ApplicationBundleDeployService.BundleSqlBinding binding : manifest.bindings()) {
            String path = "bindings[" + index + "]";
            if (isBlank(binding.objectPath())) {
                builder.addIssue(BundleValidationIssue.error(
                        "BINDING_OBJECT_PATH_REQUIRED", path, "objectPath is required",
                        "Set bindings[].objectPath to the target object.", BundleValidationIssue.DOC_BUNDLE));
            }
            if (isBlank(binding.variable())) {
                builder.addIssue(BundleValidationIssue.error(
                        "BINDING_VARIABLE_REQUIRED", path, "variable is required",
                        "Set bindings[].variable.", BundleValidationIssue.DOC_BUNDLE));
            }
            if (isBlank(binding.query())) {
                builder.addIssue(BundleValidationIssue.error(
                        "BINDING_QUERY_REQUIRED", path, "query is required",
                        "Set bindings[].query to a SELECT.", BundleValidationIssue.DOC_BUNDLE));
            }
            index++;
        }
    }

    /**
     * ADR-0060: script functions and blueprint-hosted functions must not live on DEVICE.
     * SQL bindings[] on DEVICE telemetry remain allowed.
     */
    private void validateLogicHosts(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        Map<String, String> pathTypes = buildPathTypeMap(manifest);
        if (manifest.functions() != null) {
            int index = 0;
            for (ApplicationBundleDeployService.BundleFunction function : manifest.functions()) {
                String objectPath = function.objectPath();
                String issuePath = "functions[" + index + "].objectPath";
                if (!isBlank(objectPath)) {
                    Optional<String> resolved = resolveType(objectPath, pathTypes);
                    if (resolved.isEmpty()) {
                        builder.addIssue(BundleValidationIssue.warning(
                                "LOGIC_HOST_UNKNOWN",
                                issuePath,
                                "Function host path '" + objectPath + "' is not declared in objects[]/blueprints[] "
                                        + "and was not found in the live tree",
                                "Declare a SINGLETON hub blueprint or objects[] entry for the host.",
                                BundleValidationIssue.DOC_LOGIC_HOST
                        ));
                    } else if (ObjectType.DEVICE.name().equalsIgnoreCase(resolved.get())) {
                        builder.addIssue(BundleValidationIssue.error(
                                "LOGIC_HOST_DEVICE",
                                issuePath,
                                "Function '" + function.functionName() + "' is hosted on DEVICE path '" + objectPath + "'",
                                "Move functions to root.platform.singleton-blueprints.{name} (SINGLETON) "
                                        + "or an INSTANCE twin — never ObjectType.DEVICE.",
                                BundleValidationIssue.DOC_LOGIC_HOST
                        ));
                    }
                }
                index++;
            }
        }
        if (manifest.blueprints() != null) {
            int index = 0;
            for (ApplicationBundleDeployService.BundleBlueprint blueprint : manifest.blueprints()) {
                // SINGLETON hubs must not materialize as DEVICE (application-principles hard rule).
                if (blueprint.type() == BlueprintType.SINGLETON
                        && blueprint.targetObjectType() == ObjectType.DEVICE) {
                    builder.addIssue(BundleValidationIssue.error(
                            "LOGIC_HOST_DEVICE",
                            "blueprints[" + index + "].targetObjectType",
                            "SINGLETON blueprint '" + blueprint.name() + "' must not use targetObjectType=DEVICE",
                            "Omit targetObjectType (defaults to CUSTOM) or set CUSTOM/APPLICATION.",
                            BundleValidationIssue.DOC_LOGIC_HOST
                    ));
                }
                index++;
            }
        }
    }

    private Map<String, String> buildPathTypeMap(ApplicationBundleDeployService.BundleManifest manifest) {
        Map<String, String> pathTypes = new HashMap<>();
        if (manifest.objects() != null) {
            for (ApplicationBundleDeployService.BundleObject object : manifest.objects()) {
                if (isBlank(object.parentPath()) || isBlank(object.name()) || isBlank(object.type())) {
                    continue;
                }
                pathTypes.put(object.parentPath() + "." + object.name(), object.type().trim().toUpperCase(Locale.ROOT));
            }
        }
        if (manifest.blueprints() != null) {
            for (ApplicationBundleDeployService.BundleBlueprint blueprint : manifest.blueprints()) {
                if (blueprint.type() != BlueprintType.SINGLETON || isBlank(blueprint.name())) {
                    continue;
                }
                String singletonPath = BlueprintCatalogRoots.SINGLETON + "." + blueprint.name();
                ObjectType target = blueprint.targetObjectType() != null ? blueprint.targetObjectType() : ObjectType.CUSTOM;
                pathTypes.putIfAbsent(singletonPath, target.name());
            }
        }
        return pathTypes;
    }

    private Optional<String> resolveType(String objectPath, Map<String, String> pathTypes) {
        // Catalog placement under singleton-blueprints.* is a logic hub by definition (ADR-0060).
        if (objectPath.startsWith(BlueprintCatalogRoots.SINGLETON + ".")) {
            String mapped = pathTypes.get(objectPath);
            if (mapped != null && !ObjectType.DEVICE.name().equalsIgnoreCase(mapped)) {
                return Optional.of(mapped);
            }
            return Optional.of(ObjectType.CUSTOM.name());
        }
        String fromManifest = pathTypes.get(objectPath);
        if (fromManifest != null) {
            return Optional.of(fromManifest);
        }
        ObjectManager objectManager = objectManagerProvider.getIfAvailable();
        if (objectManager == null) {
            return Optional.empty();
        }
        try {
            return objectManager.tree().findByPath(objectPath).map(PlatformObject::type).map(Enum::name);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private void validateDashboards(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.dashboards() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleDashboard dashboard : manifest.dashboards()) {
            String path = dashboard.path() != null ? dashboard.path() : "(unknown)";
            if (dashboard.path() == null || dashboard.path().isBlank()) {
                builder.addError("dashboard: path is required");
            }
            if (dashboard.layoutJson() == null || dashboard.layoutJson().isBlank()) {
                builder.addError("dashboard " + path + ": layoutJson is required");
                continue;
            }
            try {
                JsonNode layout = objectMapper.readTree(dashboard.layoutJson());
                validateWidgetTypes(layout, path, builder);
            } catch (Exception ex) {
                builder.addError("dashboard " + path + ": invalid layoutJson — " + ex.getMessage());
            }
        }
    }

    private void validateWidgetTypes(JsonNode layout, String dashboardPath, BundleValidationResult.Builder builder) {
        JsonNode widgets = layout.path("widgets");
        if (!widgets.isArray()) {
            return;
        }
        for (JsonNode widget : widgets) {
            String type = widget.path("type").asText("");
            if (!type.isBlank() && !KNOWN_WIDGET_TYPES.contains(type)) {
                builder.addWarning("dashboard " + dashboardPath + ": unknown widget type '" + type + "'");
            }
        }
    }

    private void validateObjects(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.objects() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleObject object : manifest.objects()) {
            if (object.parentPath() == null || object.parentPath().isBlank()) {
                builder.addError("object " + object.name() + ": parentPath is required");
            }
            if (object.name() == null || object.name().isBlank()) {
                builder.addError("object: name is required");
            }
            if (object.type() == null || object.type().isBlank()) {
                builder.addError("object " + object.name() + ": type is required");
            }
        }
    }

    private void validateReports(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.reports() == null) {
            return;
        }
        for (ApplicationBundleDeployService.BundleReport report : manifest.reports()) {
            if (report.reportId() == null || report.reportId().isBlank()) {
                builder.addError("report: reportId is required");
            }
            if (report.title() == null || report.title().isBlank()) {
                builder.addError("report " + report.reportId() + ": title is required");
            }
            String reportType = report.reportType() != null ? report.reportType().trim() : "";
            if ("tree-variables".equals(reportType)) {
                if (report.devicePathPattern() == null || report.devicePathPattern().isBlank()) {
                    builder.addError("report " + report.reportId() + ": devicePathPattern is required for tree-variables");
                }
                if (report.variableName() == null || report.variableName().isBlank()) {
                    builder.addError("report " + report.reportId() + ": variableName is required for tree-variables");
                }
            } else if (report.query() == null || report.query().isBlank()) {
                builder.addError("report " + report.reportId() + ": query is required for SQL reports");
            }
            if (report.columns() != null) {
                for (ApplicationBundleDeployService.BundleReportColumn column : report.columns()) {
                    if (column.field() == null || column.field().isBlank()) {
                        builder.addError("report " + report.reportId() + ": column field is required");
                    }
                }
            }
        }
    }

    private void validateDependencies(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        if (manifest.requires() == null || manifest.requires().isEmpty()) {
            return;
        }
        try {
            dependencyVerifier.verify(appId, manifest.requires());
        } catch (BundleDependencyException ex) {
            builder.addError("requires[]: " + ex.getMessage());
        }
    }

    private void validateLicense(
            String appId,
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        try {
            licenseVerifier.verifyForValidation(appId, manifest);
        } catch (CommercialLicenseException ex) {
            builder.addError("license: " + ex.getMessage());
        }
    }

    private void collectWouldApply(
            ApplicationBundleDeployService.BundleManifest manifest,
            BundleValidationResult.Builder builder
    ) {
        builder.addWouldApply("register");
        if (manifest.migrations() != null && !manifest.migrations().isEmpty()) {
            builder.addWouldApply("migrations");
        }
        if (manifest.functions() != null && !manifest.functions().isEmpty()) {
            builder.addWouldApply("functions");
        }
        if (manifest.bindings() != null && !manifest.bindings().isEmpty()) {
            builder.addWouldApply("bindings");
        }
        if (manifest.reports() != null && !manifest.reports().isEmpty()) {
            builder.addWouldApply("reports");
        }
        if (manifest.objects() != null && !manifest.objects().isEmpty()) {
            builder.addWouldApply("objects");
        }
        if (manifest.dashboards() != null && !manifest.dashboards().isEmpty()) {
            builder.addWouldApply("dashboards");
        }
        if (manifest.workflows() != null && !manifest.workflows().isEmpty()) {
            builder.addWouldApply("workflows");
        }
        if (manifest.blueprints() != null && !manifest.blueprints().isEmpty()) {
            builder.addWouldApply("blueprints");
        }
        if (manifest.alertRules() != null && !manifest.alertRules().isEmpty()) {
            builder.addWouldApply("alertRules");
        }
        if (manifest.correlators() != null && !manifest.correlators().isEmpty()) {
            builder.addWouldApply("correlators");
        }
        if (manifest.schedules() != null && !manifest.schedules().isEmpty()) {
            builder.addWouldApply("schedules");
        }
        if (manifest.events() != null && !manifest.events().isEmpty()) {
            builder.addWouldApply("events");
        }
        if (manifest.operatorUi() != null && !manifest.operatorUi().isEmpty()) {
            builder.addWouldApply("operatorUi");
        }
        if (manifest.operatorManifest() != null && !manifest.operatorManifest().isEmpty()) {
            builder.addWouldApply("operatorManifest");
        }
        builder.addWouldApply("snapshot");
        builder.addWouldApply("objectTreeSync");
    }
}
