package com.ispf.server.application.reference.minitec;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.application.report.ApplicationReportService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.correlator.CorrelatorActionType;
import com.ispf.server.correlator.CorrelatorPatternType;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.config.NotificationProperties;
import com.ispf.server.platform.ClusterPlatformBootstrapService;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.mimic.MimicService;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.operator.OperatorAppUiService;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.workflow.WorkflowService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the generic mini-TEC digital twin: objects, dashboards, automation, operator UI.
 */
@Component
public class MiniTecPlatformBootstrap {

    private final MiniTecBlueprintBootstrap BlueprintBootstrap;
    private final ObjectTemplateService templateService;
    private final ObjectManager objectManager;
    private final DashboardService dashboardService;
    private final MimicService mimicService;
    private final WorkflowService workflowService;
    private final ScheduleObjectService scheduleObjectService;
    private final AutomationTreeService automationTreeService;
    private final DriverRuntimeService driverRuntimeService;
    private final OperatorAppUiService operatorAppUiService;
    private final ApplicationDataService applicationDataService;
    private final ApplicationReportService applicationReportService;
    private static final String PLACEHOLDER_WEBHOOK = "https://hooks.example.local/mini-tec-sms";

    private final BootstrapProperties bootstrapProperties;
    private final ClusterPlatformBootstrapService clusterBootstrapService;
    private final NotificationProperties notificationProperties;

    public MiniTecPlatformBootstrap(
            MiniTecBlueprintBootstrap BlueprintBootstrap,
            ObjectTemplateService templateService,
            ObjectManager objectManager,
            DashboardService dashboardService,
            MimicService mimicService,
            WorkflowService workflowService,
            ScheduleObjectService scheduleObjectService,
            AutomationTreeService automationTreeService,
            DriverRuntimeService driverRuntimeService,
            OperatorAppUiService operatorAppUiService,
            ApplicationDataService applicationDataService,
            ApplicationReportService applicationReportService,
            BootstrapProperties bootstrapProperties,
            ClusterPlatformBootstrapService clusterBootstrapService,
            NotificationProperties notificationProperties
    ) {
        this.BlueprintBootstrap = BlueprintBootstrap;
        this.templateService = templateService;
        this.objectManager = objectManager;
        this.dashboardService = dashboardService;
        this.mimicService = mimicService;
        this.workflowService = workflowService;
        this.scheduleObjectService = scheduleObjectService;
        this.automationTreeService = automationTreeService;
        this.driverRuntimeService = driverRuntimeService;
        this.operatorAppUiService = operatorAppUiService;
        this.applicationDataService = applicationDataService;
        this.applicationReportService = applicationReportService;
        this.bootstrapProperties = bootstrapProperties;
        this.clusterBootstrapService = clusterBootstrapService;
        this.notificationProperties = notificationProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 22)
    public void onReady() throws Exception {
        if (!bootstrapProperties.isFixturesEnabled() || !clusterBootstrapService.shouldRunFixtureBootstrap()) {
            return;
        }
        BlueprintBootstrap.ensureMiniTecModels();
        registerApplication();
        ensureSqlSchema();
        ensureReports();
        ensureFolder();
        ensureDevice("gpu-01", "ГПУ-1", MiniTecBlueprintBootstrap.GPU_MODEL, 1);
        ensureDevice("gpu-02", "ГПУ-2", MiniTecBlueprintBootstrap.GPU_MODEL, 2);
        ensureDevice("gpu-03", "ГПУ-3", MiniTecBlueprintBootstrap.GPU_MODEL, 3);
        ensureDevice("grpb", "ГРПБ", MiniTecBlueprintBootstrap.GRPB_MODEL, 0);
        ensureDevice("rumb-10kv", "РУМБ 10/0.4 кВ", MiniTecBlueprintBootstrap.RUMB_MODEL, 0);
        ensureDevice("dgu", "ДГУ", MiniTecBlueprintBootstrap.DGU_MODEL, 0);
        ensureDevice("load-module", "Нагрузочный модуль", MiniTecBlueprintBootstrap.LOAD_MODEL, 0);
        ensureHub();
        ensureMimics();
        ensureDashboards();
        ensureWorkflows();
        ensureAutomation();
        ensureSchedule();
        ensureOperatorUi();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 5)
    public void startDriversAfterBootstrap() {
        if (!bootstrapProperties.isFixturesEnabled() || !clusterBootstrapService.shouldRunFixtureBootstrap()) {
            return;
        }
        startDrivers();
    }

    private void ensureSqlSchema() {
        try {
            applicationDataService.migrate(
                    MiniTecPaths.APP_ID,
                    "1.0.0",
                    List.of(new ApplicationDataService.MigrationScript(
                            "tec_schema",
                            """
                            CREATE TABLE IF NOT EXISTS tec_daily_journal (
                              journal_date DATE PRIMARY KEY,
                              total_energy_kwh DOUBLE PRECISION NOT NULL DEFAULT 0,
                              total_reactive_kvarh DOUBLE PRECISION NOT NULL DEFAULT 0,
                              total_running_hours DOUBLE PRECISION NOT NULL DEFAULT 0,
                              start_count INTEGER NOT NULL DEFAULT 0
                            );
                            CREATE TABLE IF NOT EXISTS tec_consumer_load (
                              consumer_code VARCHAR(64) PRIMARY KEY,
                              consumer_name VARCHAR(256) NOT NULL,
                              nominal_load_kw INTEGER NOT NULL
                            );
                            INSERT INTO tec_consumer_load (consumer_code, consumer_name, nominal_load_kw)
                            SELECT 'consumer-1', 'Потребитель 1', 2430
                            WHERE NOT EXISTS (SELECT 1 FROM tec_consumer_load WHERE consumer_code = 'consumer-1');
                            INSERT INTO tec_consumer_load (consumer_code, consumer_name, nominal_load_kw)
                            SELECT 'consumer-2', 'Потребитель 2', 1200
                            WHERE NOT EXISTS (SELECT 1 FROM tec_consumer_load WHERE consumer_code = 'consumer-2');
                            INSERT INTO tec_consumer_load (consumer_code, consumer_name, nominal_load_kw)
                            SELECT 'consumer-3', 'Резервная нагрузка', 500
                            WHERE NOT EXISTS (SELECT 1 FROM tec_consumer_load WHERE consumer_code = 'consumer-3');
                            """
                    ))
            );
        } catch (Exception ignored) {
            // schema may already exist
        }
    }

    private void registerApplication() {
        try {
            applicationDataService.register(
                    MiniTecPaths.APP_ID,
                    MiniTecPaths.DISPLAY_NAME,
                    "",
                    "app_mini_tec"
            );
        } catch (Exception ignored) {
            // already registered
        }
    }

    private void ensureReports() {
        try {
            applicationReportService.deploy(
                    MiniTecPaths.APP_ID,
                    new ApplicationReportService.DeployReportRequest(
                            "tec-daily-energy",
                            "Суточный журнал энергии",
                            "Учёт кВт·ч и наработки",
                            "sql",
                            null,
                            null,
                            """
                            SELECT journal_date, total_energy_kwh, total_reactive_kvarh, \
                            total_running_hours, start_count FROM tec_daily_journal \
                            ORDER BY journal_date DESC""",
                            List.of(),
                            List.of(
                                    new ApplicationReportService.ReportColumn("journal_date", "Дата"),
                                    new ApplicationReportService.ReportColumn("total_energy_kwh", "кВт·ч"),
                                    new ApplicationReportService.ReportColumn("total_reactive_kvarh", "кВАр·ч"),
                                    new ApplicationReportService.ReportColumn("total_running_hours", "Наработка, ч"),
                                    new ApplicationReportService.ReportColumn("start_count", "Пуски")
                            ),
                            365
                    )
            );
            applicationReportService.deploy(
                    MiniTecPaths.APP_ID,
                    new ApplicationReportService.DeployReportRequest(
                            "tec-gpu-run-hours",
                            "Наработка ГПУ",
                            "Показатели эксплуатации газопоршневых модулей",
                            "tree-variables",
                            MiniTecPaths.FOLDER + ".gpu-*",
                            "runningHours",
                            null,
                            List.of(),
                            List.of(
                                    new ApplicationReportService.ReportColumn("devicepath", "Агрегат"),
                                    new ApplicationReportService.ReportColumn("value", "Наработка, ч")
                            ),
                            10
                    )
            );
        } catch (Exception ignored) {
            // reports may already exist or SQL schema not ready yet
        }
    }

    private void ensureFolder() {
        if (objectManager.tree().findByPath(MiniTecPaths.FOLDER).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    MiniTecPaths.PLANT_FOLDER_NAME,
                    ObjectType.CUSTOM,
                    MiniTecPaths.DISPLAY_NAME,
                    "Gas piston mini power plant reference digital twin",
                    null
            );
        }
    }

    private void ensureDevice(String name, String displayName, String model, int unitIndex) {
        String path = MiniTecPaths.FOLDER + "." + name;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(MiniTecPaths.FOLDER, name, ObjectType.DEVICE, displayName, "", model);
        }
        templateService.applyTemplate(path, model);
        if (unitIndex > 0) {
            String config = String.format(MiniTecBlueprintBootstrap.GPU_DRIVER_CONFIG_TEMPLATE, "1480", unitIndex);
            setStringVar(path, "driverConfigJson", config);
        }
    }

    private void ensureHub() {
        String path = MiniTecPaths.STATION_HUB;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(MiniTecPaths.FOLDER, "station-hub", ObjectType.CUSTOM, "Станционный hub", "", MiniTecBlueprintBootstrap.HUB_MODEL);
        }
        templateService.applyTemplate(path, MiniTecBlueprintBootstrap.HUB_MODEL);
    }

    private void setStringVar(String path, String name, String value) {
        PlatformObject node = objectManager.require(path);
        node.setVariableValue(
                name,
                DataRecord.single(DataSchema.builder("v").field("value", FieldType.STRING).build(), Map.of("value", value))
        );
        objectManager.persistNodeTree(path);
    }

    private void ensureMimics() {
        ensureMimic(MiniTecPaths.MIMIC_SINGLE_LINE, "Однолинейная схема Мини-ТЭЦ", MiniTecMimicDocument.DIAGRAM_JSON);
        ensureMimic(MiniTecPaths.MIMIC_ZONE_GAS, "Зона газа", MiniTecMimicDocument.ZONE_GAS_JSON);
        ensureMimic(MiniTecPaths.MIMIC_ZONE_ELECTRICAL, "Зона электроснабжения", MiniTecMimicDocument.ZONE_ELECTRICAL_JSON);
    }

    private void ensureMimic(String path, String title, String diagramJson) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            int dot = path.lastIndexOf('.');
            objectManager.create(path.substring(0, dot), path.substring(dot + 1), ObjectType.MIMIC, title, "", "mimic-v1");
        }
        mimicService.ensureMimicStructure(path);
        mimicService.updateTitle(path, title);
        mimicService.saveDiagram(path, diagramJson);
        mimicService.getMimic(path);
    }

    private void ensureDashboards() {
        ensureDashboard(MiniTecPaths.DASHBOARD_HMI, "Операторская мнемосхема", MiniTecFixtureDocuments.dashboardLayout("mini-tec-hmi"));
        ensureDashboard(MiniTecPaths.DASHBOARD_OVERVIEW, "Станционная сводка", MiniTecFixtureDocuments.dashboardLayout("mini-tec-overview"));
        ensureDashboard(MiniTecPaths.DASHBOARD_SINGLE_LINE, "Однолинейная схема", MiniTecFixtureDocuments.dashboardLayout("mini-tec-single-line"));
        ensureDashboard(MiniTecPaths.DASHBOARD_KPI, "KPI станции", MiniTecFixtureDocuments.dashboardLayout("mini-tec-kpi"));
        ensureDashboard(MiniTecPaths.DASHBOARD_TRENDS, "Тренды", MiniTecFixtureDocuments.dashboardLayout("mini-tec-trends"));
        ensureDashboard(MiniTecPaths.DASHBOARD_GPU_DETAIL, "ГПУ — детально", MiniTecFixtureDocuments.dashboardLayout("mini-tec-gpu-detail"));
        ensureDashboard(MiniTecPaths.DASHBOARD_GRPB, "ГРПБ", MiniTecFixtureDocuments.dashboardLayout("mini-tec-grpb"));
        ensureDashboard(MiniTecPaths.DASHBOARD_RUMB, "РУМБ 10/0.4 кВ", MiniTecFixtureDocuments.dashboardLayout("mini-tec-rumb"));
        ensureDashboard(MiniTecPaths.DASHBOARD_DGU, "ДГУ", MiniTecFixtureDocuments.dashboardLayout("mini-tec-dgu"));
        ensureDashboard(MiniTecPaths.DASHBOARD_LOAD, "Нагрузочный модуль", MiniTecFixtureDocuments.dashboardLayout("mini-tec-load-module"));
        ensureDashboard(MiniTecPaths.DASHBOARD_PROTECTIONS, "Защиты", MiniTecFixtureDocuments.dashboardLayout("mini-tec-protections"));
        ensureDashboard(MiniTecPaths.DASHBOARD_EXPLOITATION, "Эксплуатация", MiniTecFixtureDocuments.dashboardLayout("mini-tec-exploitation"));
    }

    private void ensureDashboard(String path, String title, String layout) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            int dot = path.lastIndexOf('.');
            objectManager.create(path.substring(0, dot), path.substring(dot + 1), ObjectType.DASHBOARD, title, "", "dashboard-v1");
        }
        dashboardService.ensureDashboardStructure(path);
        dashboardService.updateTitle(path, title);
        dashboardService.saveLayout(path, layout);
        dashboardService.updateRefreshInterval(path, 3000);
    }

    private void ensureWorkflows() throws Exception {
        ensureWorkflow(MiniTecPaths.WORKFLOW_GAS_TRIP, MiniTecWorkflowDefinitions.GAS_EMERGENCY_TRIP);
        ensureWorkflow(MiniTecPaths.WORKFLOW_LOAD_UNLOAD, MiniTecWorkflowDefinitions.LOAD_AUTO_UNLOAD);
        ensureWorkflow(MiniTecPaths.WORKFLOW_GPU_START, MiniTecWorkflowDefinitions.GPU_START_SEQUENCE);
        ensureWorkflow(MiniTecPaths.WORKFLOW_ACK, MiniTecWorkflowDefinitions.ACK_PROTECTION);
        ensureWorkflow(MiniTecPaths.WORKFLOW_SHIFT_HANDOVER, MiniTecWorkflowDefinitions.SHIFT_HANDOVER);
    }

    private void ensureWorkflow(String path, String bpmn) throws Exception {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            int dot = path.lastIndexOf('.');
            objectManager.create(path.substring(0, dot), path.substring(dot + 1), ObjectType.WORKFLOW, path.substring(dot + 1), "", "workflow-v1");
        }
        workflowService.ensureWorkflowStructure(path);
        workflowService.saveBpmn(path, bpmn);
        workflowService.updateStatus(path, WorkflowLifecycleStatus.ACTIVE);
    }

    private void ensureAutomation() {
        ensureAlert("Mini-TEC GPU-1 overload", MiniTecPaths.GPU_01, "protOverload",
                "self.protOverload[\"value\"] == true", "gpuProtOverload", "protOverload", false, null);
        ensureAlert("Mini-TEC GPU-2 overload", MiniTecPaths.GPU_02, "protOverload",
                "self.protOverload[\"value\"] == true", "gpuProtOverload", "protOverload", false, null);
        ensureAlert("Mini-TEC GPU-3 overload", MiniTecPaths.GPU_03, "protOverload",
                "self.protOverload[\"value\"] == true", "gpuProtOverload", "protOverload", false, null);
        ensureAlert("Mini-TEC bus undervoltage", MiniTecPaths.STATION_HUB, "busUndervoltage",
                "self.busUndervoltage[\"value\"] == true", "busProtUndervoltage", "busUndervoltage", false, null);
        ensureAlert("Mini-TEC bus frequency low", MiniTecPaths.STATION_HUB, "busFrequencyLow",
                "self.busFrequencyLow[\"value\"] == true", "busProtUndervoltage", "busFrequencyLow", false, null);
        ensureAlert("Mini-TEC bus frequency high", MiniTecPaths.STATION_HUB, "busFrequencyHigh",
                "self.busFrequencyHigh[\"value\"] == true", "busProtUndervoltage", "busFrequencyHigh", false, null);
        ensureAlert("Mini-TEC GRPB gas leak", MiniTecPaths.GRPB, "gasLeak",
                "self.gasLeak[\"value\"] == true", "grpbGasLeak", "gasLeak", false, null);
        ensureAlert("Mini-TEC GRPB fire", MiniTecPaths.GRPB, "fireAlarm",
                "self.fireAlarm[\"value\"] == true", "grpbFire", "fireAlarm", true,
                "duty@plant.local|Пожар на ГРПБ|Активирован датчик пожара на ГРПБ мини-ТЭЦ");
        ensureAlert("Mini-TEC GRPB PZK", MiniTecPaths.GRPB, "pzkTripped",
                "self.pzkTripped[\"value\"] == true", "grpbGasLeak", "pzkTripped", false, null);
        ensureAlert("Mini-TEC station underpower", MiniTecPaths.STATION_HUB, "stationUnderpower",
                "self.stationUnderpower[\"value\"] == true", "stationUnderpower", "stationUnderpower", false, null);

        ensureCorrelator("Mini-TEC latch alarm", MiniTecPaths.STATION_HUB, "gpuProtOverload",
                CorrelatorActionType.SET_VARIABLE, "alarmLatched=true");
        ensureCorrelator("Mini-TEC gas trip workflow", MiniTecPaths.GRPB, "grpbFire",
                CorrelatorActionType.RUN_WORKFLOW, MiniTecPaths.WORKFLOW_GAS_TRIP);
        ensureCorrelator("Mini-TEC load auto unload", MiniTecPaths.STATION_HUB, "stationUnderpower",
                CorrelatorActionType.RUN_WORKFLOW, MiniTecPaths.WORKFLOW_LOAD_UNLOAD);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 21)
    public void reconcileMiniTecAlertsOnReady() {
        if (!clusterBootstrapService.shouldRunFixtureBootstrap()) {
            return;
        }
        if (objectManager.tree().findByPath(MiniTecPaths.FOLDER).isEmpty()) {
            return;
        }
        reconcilePlaceholderNotificationChannels();
    }

    /** Clears demo placeholder webhook left from earlier bootstrap versions. */
    private void reconcilePlaceholderNotificationChannels() {
        String fireRulePath = AutomationTreeService.rulePathForName("Mini-TEC GRPB fire");
        if (objectManager.tree().findByPath(fireRulePath).isEmpty()) {
            return;
        }
        var rule = automationTreeService.getAlertRule(fireRulePath);
        boolean placeholderWebhook = PLACEHOLDER_WEBHOOK.equals(rule.notificationWebhookUrl());
        boolean emailWithoutRelay = rule.notificationEmailTarget() != null
                && !rule.notificationEmailTarget().isBlank()
                && !hasNotificationRelay();
        if (!placeholderWebhook && !emailWithoutRelay) {
            return;
        }
        automationTreeService.updateAlertRule(
                fireRulePath,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                placeholderWebhook ? "" : null,
                emailWithoutRelay ? "" : null
        );
    }

    private boolean hasNotificationRelay() {
        String relay = notificationProperties.getEmailRelayUrl();
        return relay != null && !relay.isBlank();
    }

    private void ensureSchedule() {
        scheduleObjectService.ensureCatalog();
        String path = scheduleObjectService.pathForScheduleId(MiniTecPaths.SCHEDULE_JOURNAL_ETL);
        if (objectManager.tree().findByPath(path).isEmpty()) {
            scheduleObjectService.create(
                    MiniTecPaths.SCHEDULE_JOURNAL_ETL,
                    "Мини-ТЭЦ: суточный журнал",
                    "Агрегация кВт·ч и наработки в tec_daily_journal",
                    true,
                    3_600_000L,
                    MiniTecPaths.STATION_HUB,
                    "aggregate_daily_journal"
            );
        }
    }

    private void ensureAlert(
            String name,
            String objectPath,
            String watch,
            String condition,
            String event,
            String payload,
            boolean email,
            String emailTarget
    ) {
        String path = AutomationTreeService.rulePathForName(name);
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        String webhook = null;
        String target = email && hasNotificationRelay() ? emailTarget : null;
        automationTreeService.createAlertRule(
                name, objectPath, watch, condition, event, payload,
                true, true, 0, false, "HIGH", false, webhook, target
        );
    }

    private void ensureCorrelator(String name, String objectPath, String event, CorrelatorActionType action, String target) {
        String path = AutomationTreeService.correlatorPathForName(name);
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        automationTreeService.createCorrelator(
                name, objectPath, CorrelatorPatternType.COUNT, event, null,
                0, 1, 60, 0, action, target, null, true
        );
    }

    private void ensureOperatorUi() throws Exception {
        List<Map<String, String>> dashboards = List.of(
                entry(MiniTecPaths.DASHBOARD_HMI, "Мнемосхема"),
                entry(MiniTecPaths.DASHBOARD_OVERVIEW, "Сводка"),
                entry(MiniTecPaths.DASHBOARD_SINGLE_LINE, "Однолинейная"),
                entry(MiniTecPaths.DASHBOARD_KPI, "KPI"),
                entry(MiniTecPaths.DASHBOARD_TRENDS, "Тренды"),
                entry(MiniTecPaths.DASHBOARD_GPU_DETAIL, "ГПУ"),
                entry(MiniTecPaths.DASHBOARD_GRPB, "ГРПБ"),
                entry(MiniTecPaths.DASHBOARD_RUMB, "РУМБ"),
                entry(MiniTecPaths.DASHBOARD_DGU, "ДГУ"),
                entry(MiniTecPaths.DASHBOARD_LOAD, "Нагрузочный модуль"),
                entry(MiniTecPaths.DASHBOARD_PROTECTIONS, "Защиты"),
                entry(MiniTecPaths.DASHBOARD_EXPLOITATION, "Эксплуатация")
        );
        operatorAppUiService.saveUi(
                MiniTecPaths.APP_ID,
                MiniTecPaths.DISPLAY_NAME,
                MiniTecPaths.DASHBOARD_HMI,
                dashboards,
                miniTecAlarmBarConfig()
        );
    }

    private Map<String, Object> miniTecAlarmBarConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("soundEnabled", true);
        config.put("soundUrl", "/sounds/alarm.wav");
        config.put("minLevel", "WARNING");
        config.put("position", "top");
        config.put("rules", List.of(
                alarmRule(
                        "grpb-fire",
                        List.of("grpbFire", "fireAlarm"),
                        MiniTecPaths.GRPB,
                        "ERROR",
                        "Пожар: {{eventName}}",
                        "#450a0a", "#fee2e2", "#ef4444",
                        MiniTecPaths.DASHBOARD_GRPB,
                        "acknowledgeAlarm"
                ),
                alarmRule(
                        "grpb-gas",
                        List.of("grpbGasLeak", "gasLeak"),
                        MiniTecPaths.GRPB,
                        "ERROR",
                        "Утечка газа: {{eventName}} — отсечь газ, квитировать",
                        "#431407", "#ffedd5", "#f97316",
                        MiniTecPaths.DASHBOARD_HMI,
                        "acknowledgeAlarm"
                ),
                alarmRule(
                        "gpu-overload",
                        List.of("gpuProtOverload", "overloadTrip"),
                        MiniTecPaths.GPU_01,
                        "WARNING",
                        "Перегрузка: {{eventName}}",
                        "#422006", "#fef3c7", "#f59e0b",
                        MiniTecPaths.DASHBOARD_HMI,
                        null
                ),
                alarmRule(
                        "station-underpower",
                        List.of("stationUnderpower"),
                        MiniTecPaths.STATION_HUB,
                        "WARNING",
                        "Недомощность — сбросить нагрузку",
                        "#422006", "#fef3c7", "#f59e0b",
                        MiniTecPaths.DASHBOARD_HMI,
                        null
                ),
                alarmRule(
                        "open-report",
                        List.of("openOperatorReport"),
                        null,
                        "INFO",
                        "Открыть отчёт",
                        "#1e3a5f", "#e0f2fe", "#38bdf8",
                        null,
                        null
                )
        ));
        return config;
    }

    private static Map<String, Object> alarmRule(
            String id,
            List<String> eventNames,
            String objectPathPrefix,
            String minLevel,
            String title,
            String background,
            String text,
            String border,
            String dashboardPath,
            String acknowledgeFunction
    ) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("id", id);
        rule.put("eventNames", eventNames);
        if (objectPathPrefix != null) {
            rule.put("objectPathPrefix", objectPathPrefix);
        }
        rule.put("minLevel", minLevel);
        rule.put("title", title);
        rule.put("fields", List.of(
                Map.of("label", "Событие", "source", "eventName"),
                Map.of("label", "Объект", "source", "objectPath"),
                Map.of("label", "Время", "source", "timestamp")
        ));
        rule.put("colors", Map.of(
                "background", background,
                "text", text,
                "border", border,
                "accent", border
        ));
        Map<String, Object> actions = new LinkedHashMap<>();
        if (dashboardPath != null) {
            actions.put("dashboardPath", dashboardPath);
        }
        actions.put("selectionKey", "devicePath");
        actions.put("reportFromPayload", "reportPath");
        if (acknowledgeFunction != null) {
            actions.put("acknowledgeFunction", acknowledgeFunction);
        }
        rule.put("actions", actions);
        rule.put("persistUntilDismiss", true);
        return rule;
    }

    private static Map<String, String> entry(String path, String title) {
        return Map.of("path", path, "title", title);
    }

    private void startDrivers() {
        for (String path : List.of(
                MiniTecPaths.GPU_01, MiniTecPaths.GPU_02, MiniTecPaths.GPU_03,
                MiniTecPaths.GRPB, MiniTecPaths.RUMB, MiniTecPaths.DGU, MiniTecPaths.LOAD_MODULE
        )) {
            try {
                driverRuntimeService.start(path);
            } catch (Exception ex) {
                // driver may already run
            }
        }
    }
}
