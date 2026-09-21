package com.ispf.server.application.bundle;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.EventDescriptor;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintAlertTemplate;
import com.ispf.plugin.blueprint.BlueprintBindingRule;
import com.ispf.plugin.blueprint.BlueprintSqlBindingTemplate;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import com.ispf.core.model.DataSchema;
import com.ispf.server.application.api.ApplicationController;
import com.ispf.server.application.bundle.BundleTreeArtifactsApplier.BundleApplyLog;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.application.tree.ApplicationObjectTreeService;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.migration.MigrationObjectService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.report.ReportService;
import com.ispf.server.operator.OperatorAppObjectTreeService;
import com.ispf.server.license.CommercialBundleLicenseVerifier;
import com.ispf.server.application.uipack.HostedUiPackLinkEnricher;
import com.ispf.server.application.test.FunctionTestRunner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApplicationBundleDeployService {

    private final ApplicationDataService dataService;
    private final ApplicationObjectTreeService objectTreeService;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ObjectMapper objectMapper;
    private final DataSourceObjectService dataSourceObjectService;
    private final MigrationObjectService migrationObjectService;
    private final ObjectManager objectManager;
    private final CommercialBundleLicenseVerifier licenseVerifier;
    private final BundleDependencyVerifier dependencyVerifier;
    private final BundleVisualGroupService bundleVisualGroupService;
    private final BundleTreeArtifactsApplier treeArtifacts;
    private final BundleOperatorUiSync operatorUiSync;
    private final HostedUiPackLinkEnricher hostedUiPackLinkEnricher;
    private final FunctionTestRunner functionTestRunner;

    public ApplicationBundleDeployService(
            ApplicationDataService dataService,
            ApplicationObjectTreeService objectTreeService,
            ApplicationBundleSnapshotStore snapshotStore,
            ObjectMapper objectMapper,
            DataSourceObjectService dataSourceObjectService,
            MigrationObjectService migrationObjectService,
            ObjectManager objectManager,
            CommercialBundleLicenseVerifier licenseVerifier,
            BundleDependencyVerifier dependencyVerifier,
            BundleVisualGroupService bundleVisualGroupService,
            BundleTreeArtifactsApplier treeArtifacts,
            BundleOperatorUiSync operatorUiSync,
            HostedUiPackLinkEnricher hostedUiPackLinkEnricher,
            FunctionTestRunner functionTestRunner
    ) {
        this.dataService = dataService;
        this.objectTreeService = objectTreeService;
        this.snapshotStore = snapshotStore;
        this.objectMapper = objectMapper;
        this.dataSourceObjectService = dataSourceObjectService;
        this.migrationObjectService = migrationObjectService;
        this.objectManager = objectManager;
        this.licenseVerifier = licenseVerifier;
        this.dependencyVerifier = dependencyVerifier;
        this.bundleVisualGroupService = bundleVisualGroupService;
        this.treeArtifacts = treeArtifacts;
        this.operatorUiSync = operatorUiSync;
        this.hostedUiPackLinkEnricher = hostedUiPackLinkEnricher;
        this.functionTestRunner = functionTestRunner;
    }

    public Map<String, Object> deploy(String appId, BundleManifest manifest) {
        return deploy(appId, manifest, false);
    }

    public Map<String, Object> deploy(String appId, BundleManifest manifest, boolean trustedMarketplaceFreeInstall) {
        return deploy(appId, manifest, trustedMarketplaceFreeInstall, true);
    }

    /**
     * @param verifyLicense when false, caller already verified the license against the raw request map
     *                      (avoids DTO round-trip contentSha256 mismatch vs {@code sign-bundle.py})
     */
    public Map<String, Object> deploy(
            String appId,
            BundleManifest manifest,
            boolean trustedMarketplaceFreeInstall,
            boolean verifyLicense
    ) {
        return deploy(appId, manifest, trustedMarketplaceFreeInstall, verifyLicense, false);
    }

    public Map<String, Object> deploy(
            String appId,
            BundleManifest manifest,
            boolean trustedMarketplaceFreeInstall,
            boolean verifyLicense,
            boolean runTests
    ) {
        BundleSemverSupport.requireValid(manifest.version());
        if (verifyLicense) {
            licenseVerifier.verifyOrWarn(appId, manifest, trustedMarketplaceFreeInstall);
        }
        dependencyVerifier.verify(appId, manifest.requires());
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String displayName = manifest.displayName() != null && !manifest.displayName().isBlank()
                ? manifest.displayName()
                : appId;

        try {
            dataService.register(
                    appId,
                    displayName,
                    manifest.tablePrefix(),
                    manifest.schemaName()
            );
            applied.add("register");
        } catch (Exception ex) {
            errors.add("register: " + ex.getMessage());
        }

        String dataSourcePath = dataSourceObjectService.pathForNodeName(appId);
        treeArtifacts.apply(appId, manifest, false, dataSourcePath, new BundleApplyLog(applied, skipped, errors));

        if (manifest.migrations() != null && !manifest.migrations().isEmpty()) {
            try {
                for (BundleMigration script : manifest.migrations()) {
                    migrationObjectService.upsert(new MigrationObjectService.MigrationDefinition(
                            "",
                            script.id(),
                            manifest.version(),
                            dataSourcePath,
                            script.sql()
                    ));
                }
                List<String> appliedMigrations = migrationObjectService.applyPending(
                        manifest.version(),
                        dataSourcePath
                );
                applied.addAll(appliedMigrations.stream().map(id -> "migration:" + id).toList());
            } catch (Exception ex) {
                errors.add("migrations: " + ex.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("appId", appId);
        response.put("version", manifest.version());
        response.put("applied", applied);
        response.put("skipped", skipped);
        response.put("errors", errors);
        response.put("dataSourcePath", dataSourcePath);
        response.put("objectTree", "tree-first");
        String applicationPath = applicationTreePath(appId);
        response.put("applicationPath", applicationPath);

        // Tree sync can add errors; snapshot must be recorded only after the full outcome is known
        // so a post-activate sync failure cannot leave a bad deploy as findActive().
        syncApplicationTree(appId, displayName, manifest, applied, errors, response);

        if (runTests) {
            List<FunctionTestRunner.TestResult> testResults = runTests(appId, manifest);
            response.put("testResults", testResults);
            List<String> failedTests = testResults.stream()
                    .filter(result -> !"PASS".equals(result.status()))
                    .map(FunctionTestRunner.TestResult::id)
                    .toList();
            if (!failedTests.isEmpty()) {
                errors.add("tests: failed " + String.join(", ", failedTests));
            }
        }

        try {
            String manifestJson = objectMapper.writeValueAsString(manifest);
            String operatorManifestJson = manifest.operatorManifest() != null
                    ? objectMapper.writeValueAsString(manifest.operatorManifest())
                    : null;
            boolean activateSnapshot = shouldActivateDeploySnapshot(errors);
            snapshotStore.recordDeployment(
                    appId,
                    manifest.version(),
                    manifestJson,
                    operatorManifestJson,
                    activateSnapshot
            );
            response.put("snapshot", activateSnapshot ? "recorded-active" : "recorded-inactive");
            response.put("snapshotActive", activateSnapshot);
        } catch (Exception ex) {
            errors.add("snapshot: " + ex.getMessage());
            response.put("snapshot", "failed");
            response.put("snapshotActive", false);
        }

        response.put("status", finalizeDeployStatus(applied, errors));
        response.put("errors", errors);
        response.put("failedSteps", List.copyOf(errors));
        response.put("applied", applied);

        if (shouldCompensateFailedDeploy(errors)) {
            List<String> compensated = compensateFailedDeploy(appId, manifest);
            if (!compensated.isEmpty()) {
                response.put("compensated", compensated);
            }
        }

        return response;
    }

    public List<FunctionTestRunner.TestResult> runTests(String appId) {
        try {
            BundleManifest manifest = objectMapper.readValue(
                    snapshotStore.findActive(appId)
                            .orElseThrow(() -> new IllegalArgumentException("No active bundle for app: " + appId))
                            .manifestJson(),
                    BundleManifest.class
            );
            return runTests(appId, manifest);
        } catch (Exception ex) {
            return List.of(new FunctionTestRunner.TestResult(
                    "bundle",
                    "bundle",
                    "FAIL",
                    List.of(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()),
                    Map.of("appId", appId)
            ));
        }
    }

    private List<FunctionTestRunner.TestResult> runTests(String appId, BundleManifest manifest) {
        if (manifest.tests() == null || manifest.tests().isEmpty()) {
            return List.of();
        }
        List<FunctionTestRunner.TestResult> results = new ArrayList<>();
        for (BundleTest test : manifest.tests()) {
            results.add(functionTestRunner.run(toTestSpec(appId, test)));
        }
        return List.copyOf(results);
    }

    private FunctionTestRunner.TestSpec toTestSpec(String appId, BundleTest test) {
        return new FunctionTestRunner.TestSpec(
                test.id() != null && !test.id().isBlank() ? test.id() : appId + "-test",
                test.kind(),
                test.objectPath(),
                test.functionName(),
                test.variable(),
                test.input(),
                test.fixtureSql(),
                test.fields(),
                test.expect(),
                test.rollback() == null || test.rollback()
        );
    }

    /**
     * Best-effort rollback of tree artifacts when deploy did not activate a snapshot.
     * First install: remove manifest-managed paths. Upgrade with prior active snapshot:
     * visual groups only (do not delete paths owned by the previous version).
     */
    private List<String> compensateFailedDeploy(String appId, BundleManifest manifest) {
        List<String> compensated = new ArrayList<>();
        try {
            if (snapshotStore.findActive(appId).isPresent()) {
                bundleVisualGroupService.removeAllBundleGroups(appId);
                compensated.add("visualGroups:removed");
                return compensated;
            }
            for (String path : BundleVisualGroupService.managedRemovalPaths(appId, manifest)) {
                try {
                    if (objectManager.tree().findByPath(path).isPresent()) {
                        objectManager.delete(path);
                        compensated.add("removed:" + path);
                    }
                } catch (Exception ex) {
                    compensated.add("removeFailed:" + path + ": " + ex.getMessage());
                }
            }
            bundleVisualGroupService.removeAllBundleGroups(appId);
            compensated.add("visualGroups:removed");
        } catch (Exception ex) {
            compensated.add("compensate: " + ex.getMessage());
        }
        return compensated;
    }

    static boolean shouldCompensateFailedDeploy(List<String> errors) {
        return !shouldActivateDeploySnapshot(errors);
    }

    /**
     * Honest deploy outcome: OK when no errors; FAILED when nothing applied; otherwise PARTIAL.
     */
    static String finalizeDeployStatus(List<String> applied, List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "OK";
        }
        if (applied == null || applied.isEmpty()) {
            return "FAILED";
        }
        return "PARTIAL";
    }

    /**
     * Activate findActive() only for fully successful deploys (no soft-step or sync errors).
     * PARTIAL/FAILED attempts are still audited with {@code is_active=false}.
     */
    static boolean shouldActivateDeploySnapshot(List<String> errors) {
        return errors == null || errors.isEmpty();
    }

    public Map<String, Object> createBundleObjects(String appId) {
        return syncTreeArtifactsFromActiveManifest(appId, true);
    }

    public Map<String, Object> updateBundleObjects(String appId) {
        return syncTreeArtifactsFromActiveManifest(appId, false);
    }

    public Map<String, Object> removeBundleObjects(String appId) throws Exception {
        BundleManifest manifest = requireActiveManifest(appId);
        List<String> removed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String path : BundleVisualGroupService.managedRemovalPaths(appId, manifest)) {
            try {
                if (objectManager.tree().findByPath(path).isPresent()) {
                    objectManager.delete(path);
                    removed.add(path);
                } else {
                    skipped.add(path);
                }
            } catch (Exception ex) {
                errors.add(path + ": " + ex.getMessage());
            }
        }
        bundleVisualGroupService.removeAllBundleGroups(appId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("appId", appId);
        response.put("version", manifest.version());
        response.put("action", "remove");
        response.put("status", errors.isEmpty() ? "OK" : "PARTIAL");
        response.put("removed", removed);
        response.put("skipped", skipped);
        response.put("errors", errors);
        return response;
    }

    private BundleManifest requireActiveManifest(String appId) throws Exception {
        ApplicationBundleSnapshotStore.BundleSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalArgumentException("No active bundle deployment for app: " + appId));
        return objectMapper.readValue(snapshot.manifestJson(), BundleManifest.class);
    }

    /** Report object paths from the active application bundle (operator agent scope). */
    public List<String> activeReportPaths(String appId) {
        try {
            BundleManifest manifest = requireActiveManifest(appId);
            if (manifest.reports() == null || manifest.reports().isEmpty()) {
                return List.of();
            }
            List<String> paths = new ArrayList<>();
            for (BundleReport report : manifest.reports()) {
                paths.add(ReportService.reportPath(report.reportId()));
            }
            return paths;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> syncTreeArtifactsFromActiveManifest(String appId, boolean createMissingOnly) {
        try {
            BundleManifest manifest = requireActiveManifest(appId);
            return syncTreeArtifacts(appId, manifest, createMissingOnly);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load active manifest: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> syncTreeArtifacts(String appId, BundleManifest manifest, boolean createMissingOnly) {
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String displayName = manifest.displayName() != null && !manifest.displayName().isBlank()
                ? manifest.displayName()
                : appId;
        String dataSourcePath = dataSourceObjectService.pathForNodeName(appId);

        try {
            dataService.register(
                    appId,
                    displayName,
                    manifest.tablePrefix(),
                    manifest.schemaName()
            );
            applied.add("register");
        } catch (Exception ex) {
            errors.add("register: " + ex.getMessage());
        }

        treeArtifacts.apply(
                appId,
                manifest,
                createMissingOnly,
                dataSourcePath,
                new BundleApplyLog(applied, skipped, errors)
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("appId", appId);
        response.put("version", manifest.version());
        response.put("action", createMissingOnly ? "create" : "update");
        response.put("status", errors.isEmpty() ? "OK" : "PARTIAL");
        response.put("applied", applied);
        response.put("skipped", skipped);
        response.put("errors", errors);
        response.put("dataSourcePath", dataSourcePath);
        response.put("applicationPath", applicationTreePath(appId));
        syncApplicationTree(appId, displayName, manifest, applied, errors, response);
        return response;
    }

    private void syncApplicationTree(
            String appId,
            String displayName,
            BundleManifest manifest,
            List<String> applied,
            List<String> errors,
            Map<String, Object> response
    ) {
        try {
            objectTreeService.syncApplication(appId);
            applied.add("applicationTree:" + applicationTreePath(appId));
            operatorUiSync.sync(appId, manifest);
            applied.add("operatorApp:" + operatorAppTreePath(appId));
            List<String> visualGroupPaths = bundleVisualGroupService.syncBundle(
                    appId,
                    displayName,
                    manifest
            );
            for (String groupPath : visualGroupPaths) {
                applied.add("visualGroup:" + groupPath);
            }
            response.put("applied", applied);
        } catch (Exception ex) {
            errors.add("applicationSync: " + ex.getMessage());
            response.put("status", finalizeDeployStatus(applied, errors));
            response.put("errors", errors);
            response.put("failedSteps", List.copyOf(errors));
            response.put("applied", applied);
        }
    }

    public static String applicationTreePath(String appId) {
        return ApplicationObjectTreeService.APPLICATIONS_ROOT + "."
                + ApplicationObjectTreeService.sanitizeNodeName(appId);
    }

    public static String operatorAppTreePath(String appId) {
        return OperatorAppObjectTreeService.OPERATOR_APPS_ROOT + "."
                + ApplicationObjectTreeService.sanitizeNodeName(appId);
    }

    public Map<String, Object> rollback(String appId, String bundleVersion) throws Exception {
        ApplicationBundleSnapshotStore.BundleSnapshot snapshot = snapshotStore.findByVersion(appId, bundleVersion)
                .orElseThrow(() -> new IllegalArgumentException("Bundle version not found: " + bundleVersion));

        BundleManifest manifest = objectMapper.readValue(snapshot.manifestJson(), BundleManifest.class);
        Map<String, Object> result = deploy(appId, manifest);
        result.put("rolledBackTo", bundleVersion);
        return result;
    }

    public List<Map<String, Object>> deployHistory(String appId) {
        return snapshotStore.listHistory(appId);
    }

    public Map<String, Object> exportActiveBundle(String appId, String bundleVersion) throws Exception {
        return exportActiveBundle(appId, bundleVersion, false);
    }

    public Map<String, Object> exportActiveBundle(String appId, String bundleVersion, boolean canonical)
            throws Exception {
        ApplicationBundleSnapshotStore.BundleSnapshot snapshot = resolveExportSnapshot(appId, bundleVersion);
        Object manifest = objectMapper.readValue(snapshot.manifestJson(), Object.class);
        if (canonical && manifest instanceof Map<?, ?> manifestMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sorted =
                    com.ispf.server.license.BundleManifestCanonicalizer.sortManifest((Map<String, Object>) manifestMap);
            manifest = sorted;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("appId", appId);
        response.put("version", snapshot.bundleVersion());
        response.put("deployedAt", snapshot.deployedAt().toString());
        response.put("active", snapshot.active());
        response.put("manifest", manifest);
        return response;
    }

    private ApplicationBundleSnapshotStore.BundleSnapshot resolveExportSnapshot(
            String appId,
            String bundleVersion
    ) {
        if (bundleVersion != null && !bundleVersion.isBlank()) {
            return snapshotStore.findByVersion(appId, bundleVersion.trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Bundle version not found for app " + appId + ": " + bundleVersion
                    ));
        }
        return snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalArgumentException("No active bundle deployment for app: " + appId));
    }

    public Map<String, Object> operatorManifest(String appId) throws Exception {
        ApplicationBundleSnapshotStore.BundleSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalArgumentException("No active bundle deployment for app: " + appId));
        if (snapshot.operatorManifestJson() == null || snapshot.operatorManifestJson().isBlank()) {
            throw new IllegalArgumentException("Operator manifest not defined in active bundle");
        }
        return objectMapper.readValue(snapshot.operatorManifestJson(), new TypeReference<>() {
        });
    }

    public boolean supportsOperatorUi(String appId) {
        return snapshotStore.findActive(appId)
                .map(snapshot -> {
                    try {
                        BundleManifest manifest = objectMapper.readValue(snapshot.manifestJson(), BundleManifest.class);
                        return BundleOperatorUiSync.hasOperatorUiManifest(manifest);
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .orElse(false);
    }

    public Map<String, Object> operatorUi(String appId) throws Exception {
        ApplicationBundleSnapshotStore.BundleSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalArgumentException("No active bundle deployment for app: " + appId));
        BundleManifest manifest = objectMapper.readValue(snapshot.manifestJson(), BundleManifest.class);
        return hostedUiPackLinkEnricher.enrich(appId, BundleOperatorUiSync.resolveOperatorUi(appId, manifest));
    }

    public record BundleManifest(
            String version,
            String displayName,
            String tablePrefix,
            String schemaName,
            List<BundleObject> objects,
            List<BundleDashboard> dashboards,
            List<BundleWorkflow> workflows,
            List<BundleBlueprint> blueprints,
            List<BundleMigration> migrations,
            List<BundleFunction> functions,
            List<BundleSqlBinding> bindings,
            List<BundleReport> reports,
            List<BundleAlertRule> alertRules,
            List<BundleCorrelator> correlators,
            List<BundleSchedule> schedules,
            List<BundleAnalyticsFormula> analyticsFormulas,
            List<BundleEvent> events,
            List<BundleTest> tests,
            List<BundleDependency> requires,
            Map<String, Object> license,
            Map<String, Object> metadata,
            Map<String, Object> operatorUi,
            Map<String, Object> operatorManifest
    ) {
    }

    public record BundleObject(
            String parentPath,
            String name,
            String type,
            String displayName,
            String description,
            String templateId,
            Map<String, String> parameters
    ) {
    }

    public record BundleDashboard(
            String path,
            String title,
            String layoutJson,
            Integer refreshIntervalMs
    ) {
    }

    public record BundleWorkflow(
            String path,
            String bpmnXml,
            String status,
            String operatorAppId,
            String title,
            String inputSchemaJson,
            String outputSchemaJson,
            String toolDescription,
            String sideEffectClass,
            String webhookSlug
    ) {
    }

    public record BundleBlueprint(
            String name,
            String description,
            BlueprintType type,
            ObjectType targetObjectType,
            String suitabilityExpression,
            List<BlueprintVariableDefinition> variables,
            List<EventDescriptor> events,
            List<FunctionDescriptor> functions,
            List<BlueprintBindingRule> bindings,
            List<BlueprintSqlBindingTemplate> sqlBindings,
            List<BlueprintAlertTemplate> alertRules,
            Map<String, String> parameters
    ) {
    }

    public record BundleMigration(String id, String sql) {
    }

    public record BundleFunction(
            String objectPath,
            String functionName,
            String version,
            ApplicationController.FunctionDescriptorDto descriptor,
            ApplicationController.FunctionSourceDto source
    ) {
    }

    public record BundleSqlBinding(
            String objectPath,
            String variable,
            String query,
            String refresh,
            Long refreshIntervalMs,
            String valueField,
            String triggerObjectPath,
            String triggerFunctionName,
            Boolean enabled
    ) {
    }

    public record BundleReport(
            String reportId,
            String title,
            String description,
            String reportType,
            String devicePathPattern,
            String variableName,
            String query,
            List<String> parameters,
            List<BundleReportColumn> columns,
            Integer maxRows
    ) {
    }

    public record BundleAlertRule(
            String name,
            String objectPath,
            String watchVariable,
            String conditionExpr,
            String eventName,
            String payloadVariable,
            Boolean enabled,
            Boolean edgeTrigger,
            Integer delaySeconds,
            Boolean sustainWhileTrue
    ) {
    }

    public record BundleCorrelator(
            String name,
            String objectPath,
            String patternType,
            String eventName,
            String secondEventName,
            Integer windowSeconds,
            Integer minOccurrences,
            Integer cooldownSeconds,
            Integer sequenceGapSeconds,
            String actionType,
            String actionTarget,
            String payloadFilterExpr,
            Boolean enabled
    ) {
    }

    public record BundleReportColumn(String field, String label) {
    }

    public record BundleSchedule(
            String scheduleId,
            boolean enabled,
            long intervalMs,
            String actionType,
            Map<String, Object> action,
            String cronExpression,
            String timeZone
    ) {
        public BundleSchedule(
                String scheduleId,
                boolean enabled,
                long intervalMs,
                String actionType,
                Map<String, Object> action
        ) {
            this(scheduleId, enabled, intervalMs, actionType, action, null, null);
        }
    }

    public record BundleAnalyticsFormula(
            String id,
            String displayName,
            String kind,
            String expression,
            List<BundleAnalyticsFormulaParameter> parameters,
            String createdBy,
            Integer version
    ) {
    }

    public record BundleAnalyticsFormulaParameter(
            String name,
            String type,
            boolean required,
            String description,
            String defaultValue
    ) {
    }

    public record BundleEvent(
            String id,
            List<String> roles,
            Object payloadSchema
    ) {
    }

    public record BundleTest(
            String id,
            String kind,
            String objectPath,
            String functionName,
            String variable,
            Map<String, Object> input,
            List<String> fixtureSql,
            Map<String, Object> fields,
            Map<String, Object> expect,
            Boolean rollback
    ) {
    }

    public record BundleDependency(
            String appId,
            String minVersion
    ) {
    }
}
