package com.ispf.server.application.bundle;

import com.ispf.core.model.DataSchema;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.VisualGroupMember;
import com.ispf.server.alert.AlertRule;
import com.ispf.server.application.api.ApplicationController;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.correlator.EventCorrelator;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.migration.MigrationObjectService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.VisualGroupService;
import com.ispf.server.report.ReportService;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.workflow.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ApplicationBundlePullFromTreeService {

    static final List<String> DEFAULT_SECTIONS = List.of(
            "objects",
            "dashboards",
            "workflows",
            "reports",
            "functions",
            "bindings",
            "alertRules",
            "correlators",
            "schedules",
            "migrations",
            "events"
    );

    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ApplicationDataStore dataStore;
    private final VisualGroupService visualGroupService;
    private final ObjectManager objectManager;
    private final DashboardService dashboardService;
    private final WorkflowService workflowService;
    private final ReportService reportService;
    private final AutomationTreeService automationTreeService;
    private final SqlBindingObjectService sqlBindingObjectService;
    private final ScheduleObjectService scheduleObjectService;
    private final MigrationObjectService migrationObjectService;
    private final ApplicationFunctionStore functionStore;
    private final ApplicationEventCatalogService eventCatalogService;
    private final DataSourceObjectService dataSourceObjectService;
    private final ObjectMapper objectMapper;

    public ApplicationBundlePullFromTreeService(
            ApplicationBundleSnapshotStore snapshotStore,
            ApplicationDataStore dataStore,
            VisualGroupService visualGroupService,
            ObjectManager objectManager,
            DashboardService dashboardService,
            WorkflowService workflowService,
            ReportService reportService,
            AutomationTreeService automationTreeService,
            SqlBindingObjectService sqlBindingObjectService,
            ScheduleObjectService scheduleObjectService,
            MigrationObjectService migrationObjectService,
            ApplicationFunctionStore functionStore,
            ApplicationEventCatalogService eventCatalogService,
            DataSourceObjectService dataSourceObjectService,
            ObjectMapper objectMapper
    ) {
        this.snapshotStore = snapshotStore;
        this.dataStore = dataStore;
        this.visualGroupService = visualGroupService;
        this.objectManager = objectManager;
        this.dashboardService = dashboardService;
        this.workflowService = workflowService;
        this.reportService = reportService;
        this.automationTreeService = automationTreeService;
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.scheduleObjectService = scheduleObjectService;
        this.migrationObjectService = migrationObjectService;
        this.functionStore = functionStore;
        this.eventCatalogService = eventCatalogService;
        this.dataSourceObjectService = dataSourceObjectService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pullFromTree(String appId, PullFromTreeOptions options) throws Exception {
        PullFromTreeOptions effective = options != null ? options : PullFromTreeOptions.defaults();
        Set<String> sections = normalizeSections(effective.sections());
        ApplicationBundleDeployService.BundleManifest base = loadBaseManifest(appId, effective.mergeActive());
        String dataSourcePath = dataSourceObjectService.pathForNodeName(appId);

        Set<String> discoveredPaths = new LinkedHashSet<>();
        if (effective.paths() != null && !effective.paths().isEmpty()) {
            discoveredPaths.addAll(effective.paths());
        } else {
            discoveredPaths.addAll(discoverVisualGroupPaths(appId));
        }

        List<String> warnings = new ArrayList<>();
        Map<String, Integer> pulledCounts = new LinkedHashMap<>();

        List<ApplicationBundleDeployService.BundleObject> objects = base.objects() != null
                ? new ArrayList<>(base.objects())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleDashboard> dashboards = base.dashboards() != null
                ? new ArrayList<>(base.dashboards())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleWorkflow> workflows = base.workflows() != null
                ? new ArrayList<>(base.workflows())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleReport> reports = base.reports() != null
                ? new ArrayList<>(base.reports())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleSqlBinding> bindings = base.bindings() != null
                ? new ArrayList<>(base.bindings())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleAlertRule> alertRules = base.alertRules() != null
                ? new ArrayList<>(base.alertRules())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleCorrelator> correlators = base.correlators() != null
                ? new ArrayList<>(base.correlators())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleSchedule> schedules = base.schedules() != null
                ? new ArrayList<>(base.schedules())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleMigration> migrations = base.migrations() != null
                ? new ArrayList<>(base.migrations())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleFunction> functions = base.functions() != null
                ? new ArrayList<>(base.functions())
                : new ArrayList<>();
        List<ApplicationBundleDeployService.BundleEvent> events = base.events() != null
                ? new ArrayList<>(base.events())
                : new ArrayList<>();

        if (sections.contains("objects")) {
            objects = new ArrayList<>();
        }
        if (sections.contains("dashboards")) {
            dashboards = new ArrayList<>();
        }
        if (sections.contains("workflows")) {
            workflows = new ArrayList<>();
        }
        if (sections.contains("reports")) {
            reports = new ArrayList<>();
        }
        if (sections.contains("bindings")) {
            bindings = new ArrayList<>();
        }
        if (sections.contains("alertRules")) {
            alertRules = new ArrayList<>();
        }
        if (sections.contains("correlators")) {
            correlators = new ArrayList<>();
        }
        if (sections.contains("schedules")) {
            schedules = new ArrayList<>();
        }
        if (sections.contains("migrations")) {
            migrations = new ArrayList<>();
        }
        if (sections.contains("functions")) {
            functions = new ArrayList<>();
        }
        if (sections.contains("events")) {
            events = new ArrayList<>();
        }

        for (String path : discoveredPaths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            Optional<PlatformObject> nodeOpt = objectManager.tree().findByPath(path);
            if (nodeOpt.isEmpty()) {
                warnings.add("Missing path: " + path);
                continue;
            }
            PlatformObject node = nodeOpt.get();
            if (node.type() == ObjectType.VISUAL_GROUP) {
                continue;
            }
            try {
                switch (node.type()) {
                    case DASHBOARD -> {
                        if (!sections.contains("dashboards")) {
                            continue;
                        }
                        DashboardService.DashboardView view = dashboardService.getDashboard(path);
                        dashboards.add(new ApplicationBundleDeployService.BundleDashboard(
                                view.path(),
                                view.title(),
                                view.layoutJson(),
                                view.refreshIntervalMs()
                        ));
                    }
                    case WORKFLOW -> {
                        if (!sections.contains("workflows")) {
                            continue;
                        }
                        WorkflowService.WorkflowView view = workflowService.getWorkflow(path);
                        workflows.add(new ApplicationBundleDeployService.BundleWorkflow(
                                view.path(),
                                view.bpmnXml(),
                                view.status() != null ? view.status().name() : "DRAFT",
                                view.operatorAppId()
                        ));
                    }
                    case REPORT -> {
                        if (!sections.contains("reports")) {
                            continue;
                        }
                        ReportService.ReportView view = reportService.getReport(path);
                        if (view.dataSourcePath() != null && !view.dataSourcePath().equals(dataSourcePath)) {
                            warnings.add("Skipped report outside app data source: " + path);
                            continue;
                        }
                        reports.add(toBundleReport(path, node, view));
                    }
                    case ALERT -> {
                        if (!sections.contains("alertRules")) {
                            continue;
                        }
                        AlertRule rule = automationTreeService.getAlertRule(path);
                        alertRules.add(toBundleAlertRule(rule));
                    }
                    case CORRELATOR -> {
                        if (!sections.contains("correlators")) {
                            continue;
                        }
                        EventCorrelator correlator = automationTreeService.getCorrelator(path);
                        correlators.add(toBundleCorrelator(correlator));
                    }
                    case SCHEDULE -> {
                        if (!sections.contains("schedules")) {
                            continue;
                        }
                        ScheduleObjectService.ScheduleView view = scheduleObjectService.getByPath(path);
                        schedules.add(toBundleSchedule(view));
                    }
                    case BINDING -> {
                        if (!sections.contains("bindings")) {
                            continue;
                        }
                        SqlBindingObjectService.BindingDefinition binding = sqlBindingObjectService.getByPath(path);
                        if (binding.dataSourcePath() != null && !binding.dataSourcePath().equals(dataSourcePath)) {
                            warnings.add("Skipped binding outside app data source: " + path);
                            continue;
                        }
                        bindings.add(toBundleBinding(binding));
                    }
                    case MIGRATION -> {
                        if (!sections.contains("migrations")) {
                            continue;
                        }
                        MigrationObjectService.MigrationView view = migrationObjectService.getByPath(path);
                        if (view.dataSourcePath() != null && !view.dataSourcePath().equals(dataSourcePath)) {
                            warnings.add("Skipped migration outside app data source: " + path);
                            continue;
                        }
                        migrations.add(new ApplicationBundleDeployService.BundleMigration(
                                view.scriptId(),
                                view.sql()
                        ));
                    }
                    default -> {
                        if (!sections.contains("objects")) {
                            continue;
                        }
                        if (path.equals(ApplicationBundleDeployService.applicationTreePath(appId))
                                || path.equals(ApplicationBundleDeployService.operatorAppTreePath(appId))) {
                            continue;
                        }
                        objects.add(toBundleObject(path, node));
                    }
                }
            } catch (Exception ex) {
                warnings.add(path + ": " + ex.getMessage());
            }
        }

        if (sections.contains("functions")) {
            for (ApplicationFunctionHandler.DeployedFunction function : functionStore.listLatestByApp(appId)) {
                try {
                    functions.add(toBundleFunction(function));
                } catch (Exception ex) {
                    warnings.add("function:" + function.functionName() + ": " + ex.getMessage());
                }
            }
        }

        if (sections.contains("events")) {
            for (Map<String, Object> row : eventCatalogService.listEvents(appId)) {
                events.add(new ApplicationBundleDeployService.BundleEvent(
                        String.valueOf(row.get("id")),
                        castStringList(row.get("roles")),
                        row.get("payloadSchema")
                ));
            }
        }

        if (sections.contains("reports") && (effective.paths() == null || effective.paths().isEmpty())) {
            for (Map<String, Object> summary : reportService.listByDataSource(dataSourcePath)) {
                String reportPath = String.valueOf(summary.get("path"));
                if (reports.stream().anyMatch(report -> reportPath.equals(ReportService.reportPath(report.reportId())))) {
                    continue;
                }
                try {
                    PlatformObject node = objectManager.require(reportPath);
                    ReportService.ReportView view = reportService.getReport(reportPath);
                    reports.add(toBundleReport(reportPath, node, view));
                } catch (Exception ex) {
                    warnings.add("report:" + reportPath + ": " + ex.getMessage());
                }
            }
        }

        ApplicationBundleDeployService.BundleManifest manifest = new ApplicationBundleDeployService.BundleManifest(
                base.version(),
                base.displayName(),
                base.tablePrefix(),
                base.schemaName(),
                pick(sections.contains("objects"), objects, base.objects()),
                pick(sections.contains("dashboards"), dashboards, base.dashboards()),
                pick(sections.contains("workflows"), workflows, base.workflows()),
                base.blueprints(),
                pick(sections.contains("migrations"), migrations, base.migrations()),
                pick(sections.contains("functions"), functions, base.functions()),
                pick(sections.contains("bindings"), bindings, base.bindings()),
                pick(sections.contains("reports"), reports, base.reports()),
                pick(sections.contains("alertRules"), alertRules, base.alertRules()),
                pick(sections.contains("correlators"), correlators, base.correlators()),
                pick(sections.contains("schedules"), schedules, base.schedules()),
                pick(sections.contains("events"), events, base.events()),
                base.requires(),
                base.license(),
                base.metadata(),
                base.operatorUi(),
                base.operatorManifest()
        );

        pulledCounts.put("objects", size(manifest.objects()));
        pulledCounts.put("dashboards", size(manifest.dashboards()));
        pulledCounts.put("workflows", size(manifest.workflows()));
        pulledCounts.put("reports", size(manifest.reports()));
        pulledCounts.put("functions", size(manifest.functions()));
        pulledCounts.put("bindings", size(manifest.bindings()));
        pulledCounts.put("alertRules", size(manifest.alertRules()));
        pulledCounts.put("correlators", size(manifest.correlators()));
        pulledCounts.put("schedules", size(manifest.schedules()));
        pulledCounts.put("migrations", size(manifest.migrations()));
        pulledCounts.put("events", size(manifest.events()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("appId", appId);
        response.put("baseVersion", base.version());
        response.put("manifest", objectMapper.convertValue(manifest, new TypeReference<Map<String, Object>>() {
        }));
        response.put("discoveredPaths", List.copyOf(discoveredPaths));
        response.put("pulled", pulledCounts);
        response.put("warnings", warnings);
        return response;
    }

    private ApplicationBundleDeployService.BundleManifest loadBaseManifest(
            String appId,
            boolean mergeActive
    ) throws Exception {
        if (mergeActive) {
            Optional<ApplicationBundleSnapshotStore.BundleSnapshot> snapshot = snapshotStore.findActive(appId);
            if (snapshot.isPresent()) {
                return objectMapper.readValue(
                        snapshot.get().manifestJson(),
                        ApplicationBundleDeployService.BundleManifest.class
                );
            }
        }
        return shellManifest(appId);
    }

    private ApplicationBundleDeployService.BundleManifest shellManifest(String appId) {
        Map<String, Object> app = dataStore.findApp(appId).orElse(Map.of());
        String displayName = app.get("display_name") != null
                ? String.valueOf(app.get("display_name"))
                : appId;
        String schemaName = app.get("schema_name") != null
                ? String.valueOf(app.get("schema_name"))
                : "app_" + appId.replace('-', '_');
        String tablePrefix = app.get("table_prefix") != null
                ? String.valueOf(app.get("table_prefix"))
                : "";
        return new ApplicationBundleDeployService.BundleManifest(
                "1.0.0",
                displayName,
                tablePrefix,
                schemaName,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null
        );
    }

    List<String> discoverVisualGroupPaths(String appId) {
        Set<String> paths = new LinkedHashSet<>();
        for (String catalogRoot : BundleVisualGroupService.BUNDLE_CATALOG_ROOTS) {
            String groupPath = BundleVisualGroupService.groupPathForCatalogAndApp(catalogRoot, appId);
            if (objectManager.tree().findByPath(groupPath).isEmpty()) {
                continue;
            }
            for (VisualGroupMember member : visualGroupService.listMembers(groupPath)) {
                if (member.path() != null && !member.path().equals(groupPath)) {
                    paths.add(member.path());
                }
            }
        }
        return List.copyOf(paths);
    }

    private static Set<String> normalizeSections(List<String> sections) {
        if (sections == null || sections.isEmpty()) {
            return new LinkedHashSet<>(DEFAULT_SECTIONS);
        }
        return new LinkedHashSet<>(sections);
    }

    private ApplicationBundleDeployService.BundleObject toBundleObject(String path, PlatformObject node) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot <= 0) {
            throw new IllegalArgumentException("Invalid object path: " + path);
        }
        return new ApplicationBundleDeployService.BundleObject(
                path.substring(0, lastDot),
                path.substring(lastDot + 1),
                node.type().name(),
                node.displayName(),
                node.description(),
                node.templateId().orElse(null)
        );
    }

    private ApplicationBundleDeployService.BundleReport toBundleReport(
            String path,
            PlatformObject node,
            ReportService.ReportView view
    ) {
        return new ApplicationBundleDeployService.BundleReport(
                ReportService.reportIdFromPath(path),
                view.title(),
                node.description(),
                view.reportType(),
                view.devicePathPattern(),
                view.variableName(),
                view.query(),
                view.parameters(),
                view.columns().stream()
                        .map(column -> new ApplicationBundleDeployService.BundleReportColumn(
                                column.field(),
                                column.label()
                        ))
                        .toList(),
                view.maxRows()
        );
    }

    private ApplicationBundleDeployService.BundleAlertRule toBundleAlertRule(AlertRule rule) {
        return new ApplicationBundleDeployService.BundleAlertRule(
                rule.name(),
                rule.objectPath(),
                rule.watchVariable(),
                rule.conditionExpr(),
                rule.eventName(),
                rule.payloadVariable(),
                rule.enabled(),
                rule.edgeTrigger(),
                rule.delaySeconds(),
                rule.sustainWhileTrue()
        );
    }

    private ApplicationBundleDeployService.BundleCorrelator toBundleCorrelator(EventCorrelator correlator) {
        return new ApplicationBundleDeployService.BundleCorrelator(
                correlator.name(),
                correlator.objectPath(),
                correlator.patternType() != null ? correlator.patternType().name() : null,
                correlator.eventName(),
                correlator.secondEventName(),
                correlator.windowSeconds(),
                correlator.minOccurrences(),
                correlator.cooldownSeconds(),
                correlator.sequenceGapSeconds(),
                correlator.actionType() != null ? correlator.actionType().name() : null,
                correlator.actionTarget(),
                correlator.payloadFilterExpr(),
                correlator.enabled()
        );
    }

    private ApplicationBundleDeployService.BundleSchedule toBundleSchedule(
            ScheduleObjectService.ScheduleView view
    ) throws Exception {
        Map<String, Object> action = view.actionJson() != null && !view.actionJson().isBlank()
                ? objectMapper.readValue(view.actionJson(), new TypeReference<Map<String, Object>>() {
                })
                : Map.of(
                        "objectPath", view.objectPath() != null ? view.objectPath() : "",
                        "functionName", view.functionName() != null ? view.functionName() : ""
                );
        return new ApplicationBundleDeployService.BundleSchedule(
                view.scheduleId(),
                view.enabled(),
                view.intervalMs(),
                view.actionType(),
                action
        );
    }

    private ApplicationBundleDeployService.BundleSqlBinding toBundleBinding(
            SqlBindingObjectService.BindingDefinition binding
    ) {
        return new ApplicationBundleDeployService.BundleSqlBinding(
                binding.targetObjectPath(),
                binding.variable(),
                binding.query(),
                binding.refresh(),
                binding.refreshIntervalMs(),
                binding.valueField(),
                binding.triggerObjectPath(),
                binding.triggerFunctionName(),
                binding.enabled()
        );
    }

    private ApplicationBundleDeployService.BundleFunction toBundleFunction(
            ApplicationFunctionHandler.DeployedFunction function
    ) throws Exception {
        DataSchema inputSchema = objectMapper.readValue(function.inputSchemaJson(), DataSchema.class);
        DataSchema outputSchema = objectMapper.readValue(function.outputSchemaJson(), DataSchema.class);
        return new ApplicationBundleDeployService.BundleFunction(
                function.objectPath(),
                function.functionName(),
                function.version(),
                new ApplicationController.FunctionDescriptorDto(inputSchema, outputSchema),
                new ApplicationController.FunctionSourceDto(function.sourceType(), function.sourceBody())
        );
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static int size(List<?> values) {
        return values != null ? values.size() : 0;
    }

    private static <T> List<T> pick(boolean sectionPulled, List<T> pulled, List<T> base) {
        return sectionPulled ? pulled : base;
    }

    public record PullFromTreeOptions(
            List<String> sections,
            List<String> paths,
            boolean mergeActive
    ) {
        public static PullFromTreeOptions defaults() {
            return new PullFromTreeOptions(null, null, true);
        }
    }
}
