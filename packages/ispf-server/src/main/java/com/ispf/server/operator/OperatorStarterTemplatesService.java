package com.ispf.server.operator;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.object.ObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave 4 O1 — installable operator starter apps (dashboard-UI only).
 */
@Service
public class OperatorStarterTemplatesService {

    private static final Logger log = LoggerFactory.getLogger(OperatorStarterTemplatesService.class);

    public static final String ALARM_CONSOLE = "alarm-console";
    public static final String WORK_QUEUE = "work-queue";
    public static final String HMI_WALL = "hmi-wall";

    private final ObjectManager objectManager;
    private final DashboardService dashboardService;
    private final OperatorAppUiService operatorAppUiService;
    private final BootstrapProperties bootstrapProperties;

    public OperatorStarterTemplatesService(
            ObjectManager objectManager,
            DashboardService dashboardService,
            OperatorAppUiService operatorAppUiService,
            BootstrapProperties bootstrapProperties
    ) {
        this.objectManager = objectManager;
        this.dashboardService = dashboardService;
        this.operatorAppUiService = operatorAppUiService;
        this.bootstrapProperties = bootstrapProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(55)
    public void seedWhenFixturesEnabled() {
        if (!bootstrapProperties.isFixturesEnabled() || !bootstrapProperties.shouldSeedGeneralReferenceDemos()) {
            return;
        }
        try {
            Map<String, Object> result = installStarters(false);
            log.info("Operator starter templates: {}", result.get("installed"));
        } catch (Exception ex) {
            log.warn("Operator starter templates seed skipped: {}", ex.getMessage());
        }
    }

    public List<Map<String, Object>> listStarters() {
        return List.of(
                starterMeta(ALARM_CONSOLE, "Alarm Console", "Event feed + alarm bar for demo-sensor"),
                starterMeta(WORK_QUEUE, "Work Queue", "Operator work-queue board"),
                starterMeta(HMI_WALL, "HMI Wall", "Video-wall 2×2 host mosaic")
        );
    }

    @Transactional
    public Map<String, Object> installStarters(boolean forceLayout) throws Exception {
        List<String> installed = new ArrayList<>();
        installed.add(installAlarmConsole(forceLayout));
        installed.add(installWorkQueue(forceLayout));
        installed.add(installHmiWall(forceLayout));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("installed", installed);
        out.put("apps", listStarters());
        return out;
    }

    private String installAlarmConsole(boolean forceLayout) throws Exception {
        String dashPath = "root.platform.dashboards.alarm-console";
        ensureDashboard(dashPath, "Alarm Console", OperatorStarterDashboardLayouts.ALARM_CONSOLE, forceLayout);
        Map<String, Object> alarmBar = new LinkedHashMap<>();
        alarmBar.put("enabled", true);
        alarmBar.put("position", "top");
        alarmBar.put("minLevel", "WARNING");
        alarmBar.put(
                "rules",
                List.of(Map.of(
                        "id", "threshold-exceeded",
                        "eventNames", List.of("thresholdExceeded"),
                        "title", "Threshold exceeded",
                        "minLevel", "WARNING",
                        "acknowledgeFunction", "acknowledgeAlarm"
                ))
        );
        upsertApp(
                ALARM_CONSOLE,
                "Alarm Console",
                dashPath,
                List.of(Map.of("path", dashPath, "title", "Alarm Console")),
                alarmBar
        );
        return ALARM_CONSOLE;
    }

    private String installWorkQueue(boolean forceLayout) throws Exception {
        String dashPath = "root.platform.dashboards.work-queue";
        ensureDashboard(dashPath, "Work Queue", OperatorStarterDashboardLayouts.WORK_QUEUE, forceLayout);
        upsertApp(
                WORK_QUEUE,
                "Work Queue",
                dashPath,
                List.of(Map.of("path", dashPath, "title", "Work Queue")),
                null
        );
        return WORK_QUEUE;
    }

    private String installHmiWall(boolean forceLayout) throws Exception {
        String dashPath = "root.platform.dashboards.hmi-wall";
        ensureDashboard(dashPath, "HMI Wall", OperatorStarterDashboardLayouts.HMI_WALL, forceLayout);
        List<Map<String, String>> dashboards = new ArrayList<>();
        dashboards.add(Map.of("path", dashPath, "title", "HMI Wall"));
        addIfDashboardExists(dashboards, "root.platform.dashboards.demo-sensor", "Demo Sensor");
        addIfDashboardExists(dashboards, "root.platform.dashboards.snmp-host-monitoring", "SNMP Host");
        addIfDashboardExists(dashboards, "root.platform.dashboards.platform-metrics", "Self-Diagnostics");
        upsertApp(HMI_WALL, "HMI Wall", dashPath, dashboards, null);
        return HMI_WALL;
    }

    private void addIfDashboardExists(List<Map<String, String>> dashboards, String path, String title) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            dashboards.add(Map.of("path", path, "title", title));
        }
    }

    private void ensureDashboard(String path, String title, String layoutJson, boolean forceLayout) {
        boolean created = objectManager.tree().findByPath(path).isEmpty();
        if (created) {
            String name = path.substring(path.lastIndexOf('.') + 1);
            objectManager.create(
                    "root.platform.dashboards",
                    name,
                    ObjectType.DASHBOARD,
                    title,
                    "Operator starter template dashboard.",
                    "dashboard-v1"
            );
        }
        dashboardService.ensureDashboardStructure(path);
        PlatformObject dashboard = objectManager.require(path);
        String current = dashboard.getVariable("layout")
                .flatMap(v -> v.value())
                .filter(record -> !record.rows().isEmpty())
                .map(record -> String.valueOf(record.rows().get(0).get("value")))
                .orElse("");
        boolean empty = current.isBlank()
                || current.contains("\"widgets\":[]")
                || current.contains("\"widgets\": []");
        if (created || empty || forceLayout) {
            DataSchema stringValue = DataSchema.builder("stringValue").field("value", FieldType.STRING).build();
            DataSchema intValue = DataSchema.builder("integerValue").field("value", FieldType.INTEGER).build();
            dashboard.setVariableValue("title", DataRecord.single(stringValue, Map.of("value", title)));
            dashboard.setVariableValue(
                    "refreshIntervalMs",
                    DataRecord.single(intValue, Map.of("value", 5000))
            );
            dashboard.setVariableValue(
                    "layout",
                    DataRecord.single(stringValue, Map.of("value", layoutJson.trim()))
            );
            objectManager.persistNodeTree(path);
        }
    }

    private void upsertApp(
            String appId,
            String title,
            String defaultDashboard,
            List<Map<String, String>> dashboards,
            Map<String, Object> alarmBar
    ) throws Exception {
        try {
            Map<String, Object> existing = operatorAppUiService.getUi(appId);
            Object existingDashboards = existing.get("dashboards");
            if (existingDashboards instanceof List<?> list && !list.isEmpty()) {
                return;
            }
        } catch (IllegalArgumentException missing) {
            operatorAppUiService.createApp(appId, title);
        }
        operatorAppUiService.saveUi(appId, title, defaultDashboard, dashboards, alarmBar);
    }

    private static Map<String, Object> starterMeta(String appId, String title, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appId", appId);
        m.put("title", title);
        m.put("description", description);
        return m;
    }
}
