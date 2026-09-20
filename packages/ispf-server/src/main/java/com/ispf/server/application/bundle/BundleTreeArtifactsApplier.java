package com.ispf.server.application.bundle;

import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleAlertRule;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleBlueprint;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleCorrelator;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleDashboard;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleFunction;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleManifest;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleObject;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleReport;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleSchedule;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleSqlBinding;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleWorkflow;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.application.data.ApplicationSchemaSupport;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.report.ApplicationReportService;
import com.ispf.server.application.tree.ApplicationObjectTreeService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaService;
import com.ispf.server.report.ReportService;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the object-tree artifacts of a bundle manifest (data source, blueprints, objects,
 * dashboards, workflows, functions, SQL bindings, reports, alert rules, correlators, schedules,
 * analytics formulas, events). Every artifact is attempted independently; the outcome is
 * recorded per item in a {@link BundleApplyLog} so a deploy can finish PARTIAL instead of
 * aborting on the first failure.
 */
@Component
public class BundleTreeArtifactsApplier {

    /** Per-item bookkeeping for one apply pass; the lists are mutated in place. */
    public record BundleApplyLog(List<String> applied, List<String> skipped, List<String> errors) {
        public static BundleApplyLog empty() {
            return new BundleApplyLog(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    private final DataSourceObjectService dataSourceObjectService;
    private final BlueprintRegistry blueprintRegistry;
    private final ObjectManager objectManager;
    private final ApplicationBundleMetadataService metadataService;
    private final ApplicationFunctionStore functionStore;
    private final SqlBindingObjectService sqlBindingObjectService;
    private final ApplicationReportService reportService;
    private final ScheduleObjectService scheduleObjectService;
    private final AnalyticsFormulaService analyticsFormulaService;
    private final ApplicationEventCatalogService eventCatalogService;
    private final BundleArtifactDeployers deployers;
    private final ObjectMapper objectMapper;

    public BundleTreeArtifactsApplier(
            DataSourceObjectService dataSourceObjectService,
            BlueprintRegistry blueprintRegistry,
            ObjectManager objectManager,
            ApplicationBundleMetadataService metadataService,
            ApplicationFunctionStore functionStore,
            SqlBindingObjectService sqlBindingObjectService,
            ApplicationReportService reportService,
            ScheduleObjectService scheduleObjectService,
            AnalyticsFormulaService analyticsFormulaService,
            ApplicationEventCatalogService eventCatalogService,
            BundleArtifactDeployers deployers,
            ObjectMapper objectMapper
    ) {
        this.dataSourceObjectService = dataSourceObjectService;
        this.blueprintRegistry = blueprintRegistry;
        this.objectManager = objectManager;
        this.metadataService = metadataService;
        this.functionStore = functionStore;
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.reportService = reportService;
        this.scheduleObjectService = scheduleObjectService;
        this.analyticsFormulaService = analyticsFormulaService;
        this.eventCatalogService = eventCatalogService;
        this.deployers = deployers;
        this.objectMapper = objectMapper;
    }

    /**
     * @param createMissingOnly when true, artifacts that already exist are skipped instead of updated
     */
    public void apply(
            String appId,
            BundleManifest manifest,
            boolean createMissingOnly,
            String dataSourcePath,
            BundleApplyLog log
    ) {
        List<String> applied = log.applied();
        List<String> skipped = log.skipped();
        List<String> errors = log.errors();

        try {
            String schema = resolvePackageSchemaName(appId, manifest);
            dataSourceObjectService.ensureDataSource(
                    appId,
                    manifest.displayName() != null ? manifest.displayName() : appId,
                    schema,
                    "Package import data source"
            );
            applied.add("dataSource:" + dataSourcePath);
        } catch (Exception ex) {
            errors.add("dataSource: " + ex.getMessage());
        }

        if (manifest.blueprints() != null) {
            for (BundleBlueprint blueprint : manifest.blueprints()) {
                try {
                    if (createMissingOnly && blueprintRegistry.findByName(blueprint.name()).isPresent()) {
                        skipped.add("blueprint:" + blueprint.name());
                        continue;
                    }
                    deployers.deployBlueprint(blueprint);
                    applied.add("blueprint:" + blueprint.name());
                } catch (Exception ex) {
                    errors.add("blueprint:" + blueprint.name() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.objects() != null) {
            for (BundleObject object : manifest.objects()) {
                try {
                    String objectPath = objectManager.tree().resolveChildPath(object.parentPath(), object.name());
                    if (createMissingOnly && objectManager.tree().findByPath(objectPath).isPresent()) {
                        skipped.add("object:" + object.name());
                        continue;
                    }
                    ApplicationBundleMetadataService.DeployOutcome outcome = metadataService.deployObject(object);
                    if (outcome == ApplicationBundleMetadataService.DeployOutcome.APPLIED
                            || outcome == ApplicationBundleMetadataService.DeployOutcome.UPDATED) {
                        applied.add("object:" + object.name());
                    } else {
                        skipped.add("object:" + object.name());
                    }
                } catch (Exception ex) {
                    errors.add("object:" + object.name() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.dashboards() != null) {
            for (BundleDashboard dashboard : manifest.dashboards()) {
                try {
                    if (createMissingOnly && treePathExists(dashboard.path())) {
                        skipped.add("dashboard:" + dashboard.path());
                        continue;
                    }
                    metadataService.deployDashboard(dashboard);
                    applied.add("dashboard:" + dashboard.path());
                } catch (Exception ex) {
                    errors.add("dashboard:" + dashboard.path() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.workflows() != null) {
            for (BundleWorkflow workflow : manifest.workflows()) {
                try {
                    if (createMissingOnly && treePathExists(workflow.path())) {
                        skipped.add("workflow:" + workflow.path());
                        continue;
                    }
                    metadataService.deployWorkflow(workflow);
                    applied.add("workflow:" + workflow.path());
                } catch (Exception ex) {
                    errors.add("workflow:" + workflow.path() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.functions() != null) {
            for (BundleFunction function : manifest.functions()) {
                try {
                    if (createMissingOnly
                            && !functionStore.listVersions(appId, function.objectPath(), function.functionName())
                                    .isEmpty()) {
                        skipped.add("function:" + function.functionName());
                        continue;
                    }
                    deployers.deployFunction(appId, dataSourcePath, function);
                    applied.add("function:" + function.functionName());
                } catch (Exception ex) {
                    errors.add("function:" + function.functionName() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.bindings() != null) {
            for (BundleSqlBinding binding : manifest.bindings()) {
                try {
                    String bindingPath = bindingTreePath(binding);
                    if (createMissingOnly && treePathExists(bindingPath)) {
                        skipped.add("binding:" + binding.variable());
                        continue;
                    }
                    String bindingId = binding.objectPath().replace('.', '-') + "-" + binding.variable();
                    sqlBindingObjectService.upsert(new SqlBindingObjectService.BindingDefinition(
                            "",
                            bindingId,
                            binding.objectPath(),
                            binding.variable(),
                            dataSourcePath,
                            binding.query(),
                            binding.valueField(),
                            binding.refresh() != null ? binding.refresh() : "manual",
                            binding.refreshIntervalMs() != null ? binding.refreshIntervalMs() : 30_000L,
                            binding.triggerObjectPath() != null ? binding.triggerObjectPath() : "",
                            binding.triggerFunctionName() != null ? binding.triggerFunctionName() : "",
                            binding.enabled() == null || binding.enabled(),
                            null
                    ));
                    applied.add("binding:" + binding.variable());
                } catch (Exception ex) {
                    errors.add("binding:" + binding.variable() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.reports() != null) {
            for (BundleReport report : manifest.reports()) {
                try {
                    String reportPath = ReportService.reportPath(report.reportId());
                    if (createMissingOnly && treePathExists(reportPath)) {
                        skipped.add("report:" + report.reportId());
                        continue;
                    }
                    reportService.deploy(appId, new ApplicationReportService.DeployReportRequest(
                            report.reportId(),
                            report.title(),
                            report.description(),
                            report.reportType(),
                            report.devicePathPattern(),
                            report.variableName(),
                            report.query(),
                            report.parameters(),
                            report.columns() == null
                                    ? List.of()
                                    : report.columns().stream()
                                            .map(col -> new ApplicationReportService.ReportColumn(col.field(), col.label()))
                                            .toList(),
                            report.maxRows()
                    ));
                    applied.add("report:" + report.reportId());
                } catch (Exception ex) {
                    errors.add("report:" + report.reportId() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.alertRules() != null) {
            for (BundleAlertRule rule : manifest.alertRules()) {
                try {
                    String rulePath = AutomationTreeService.rulePathForName(rule.name());
                    if (createMissingOnly && treePathExists(rulePath)) {
                        skipped.add("alertRule:" + rule.name());
                        continue;
                    }
                    deployers.deployAlertRule(rule);
                    applied.add("alertRule:" + rule.name());
                } catch (Exception ex) {
                    errors.add("alertRule:" + rule.name() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.correlators() != null) {
            for (BundleCorrelator correlator : manifest.correlators()) {
                try {
                    String correlatorPath = AutomationTreeService.correlatorPathForName(correlator.name());
                    if (createMissingOnly && treePathExists(correlatorPath)) {
                        skipped.add("correlator:" + correlator.name());
                        continue;
                    }
                    deployers.deployCorrelator(correlator);
                    applied.add("correlator:" + correlator.name());
                } catch (Exception ex) {
                    errors.add("correlator:" + correlator.name() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.schedules() != null) {
            for (BundleSchedule schedule : manifest.schedules()) {
                try {
                    String schedulePath = scheduleTreePath(schedule);
                    if (createMissingOnly && treePathExists(schedulePath)) {
                        skipped.add("schedule:" + schedule.scheduleId());
                        continue;
                    }
                    String actionJson = objectMapper.writeValueAsString(schedule.action());
                    scheduleObjectService.upsert(new ScheduleObjectService.ScheduleDefinition(
                            "",
                            schedule.scheduleId(),
                            schedule.enabled(),
                            schedule.intervalMs(),
                            schedule.cronExpression() != null ? schedule.cronExpression() : "",
                            schedule.timeZone() != null && !schedule.timeZone().isBlank() ? schedule.timeZone() : "UTC",
                            schedule.actionType(),
                            actionJson,
                            null,
                            null
                    ));
                    applied.add("schedule:" + schedule.scheduleId());
                } catch (Exception ex) {
                    errors.add("schedule:" + schedule.scheduleId() + ": " + ex.getMessage());
                }
            }
        }

        if (manifest.analyticsFormulas() != null && !manifest.analyticsFormulas().isEmpty()) {
            try {
                analyticsFormulaService.mergeAppBundleFormulas(appId, manifest.analyticsFormulas().stream()
                        .map(formula -> BundleArtifactDeployers.toAnalyticsFormula(appId, formula))
                        .toList());
                applied.add("analyticsFormulas:" + manifest.analyticsFormulas().size());
            } catch (Exception ex) {
                errors.add("analyticsFormulas: " + ex.getMessage());
            }
        }

        if (manifest.events() != null) {
            try {
                if (createMissingOnly && !eventCatalogService.listEvents(appId).isEmpty()) {
                    skipped.add("events:" + manifest.events().size());
                } else {
                    eventCatalogService.replaceFromBundle(appId, manifest.events().stream()
                            .map(event -> new ApplicationEventCatalogService.BundleEventDefinition(
                                    event.id(),
                                    event.roles(),
                                    event.payloadSchema()
                            ))
                            .toList());
                    applied.add("events:" + manifest.events().size());
                }
            } catch (Exception ex) {
                errors.add("events: " + ex.getMessage());
            }
        }
    }

    private boolean treePathExists(String path) {
        return path != null && !path.isBlank() && objectManager.tree().findByPath(path).isPresent();
    }

    private static String bindingTreePath(BundleSqlBinding binding) {
        String bindingId = binding.objectPath().replace('.', '-') + "-" + binding.variable();
        return SqlBindingObjectService.BINDINGS_ROOT + "."
                + SqlBindingObjectService.sanitizeNodeName(bindingId);
    }

    private static String scheduleTreePath(BundleSchedule schedule) {
        return ScheduleObjectService.SCHEDULES_ROOT + "."
                + ApplicationObjectTreeService.sanitizeNodeName(schedule.scheduleId());
    }

    private static String resolvePackageSchemaName(String appId, BundleManifest manifest) {
        if (manifest.schemaName() != null && !manifest.schemaName().isBlank()) {
            return manifest.schemaName();
        }
        return ApplicationSchemaSupport.defaultSchemaName(appId);
    }
}
