package com.ispf.server.bootstrap;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.operator.OperatorAppUiService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot install of the virt-cluster reference project (same recipe as the agent
 * virtualClusterMonitoring playbook).
 */
@Service
public class VirtClusterPresetService {

    public static final String FOLDER = "root.platform.devices.virt-cluster";
    public static final String HUB = FOLDER + ".hub";
    public static final String DEV_01 = FOLDER + ".dev-01";
    public static final String DEV_02 = FOLDER + ".dev-02";
    public static final String DEV_03 = FOLDER + ".dev-03";
    public static final String OVERVIEW = "root.platform.dashboards.virt-cluster-overview";
    public static final String DETAIL = "root.platform.dashboards.virt-cluster-detail";
    public static final String LAB_CONFIG =
            "{\"profile\":\"lab\",\"sineAmplitude\":\"10\",\"sawtoothAmplitude\":\"5\","
                    + "\"triangleAmplitude\":\"5\",\"periodSec\":\"30\"}";

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();
    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();
    private static final DataSchema BOOLEAN_VALUE = DataSchema.builder("booleanValue")
            .field("value", FieldType.BOOLEAN)
            .build();

    private static final List<String> WAVE_VARS = List.of("sineWave", "sawtoothWave", "triangleWave");
    private static final Map<String, String> LAB_POINT_MAP = Map.of(
            "sineWave", "sim",
            "sawtoothWave", "sim",
            "triangleWave", "sim",
            "status", "sim"
    );

    private final ObjectManager objectManager;
    private final ObjectTemplateService objectTemplateService;
    private final LabBlueprintBootstrap labBlueprintBootstrap;
    private final DeviceProvisioningService deviceProvisioningService;
    private final DriverRuntimeService driverRuntimeService;
    private final BindingRulesService bindingRulesService;
    private final BindingDependencyIndex bindingDependencyIndex;
    private final BindingRuleEngine bindingRuleEngine;
    private final AutomationTreeService automationTreeService;
    private final DashboardService dashboardService;
    private final OperatorAppUiService operatorAppUiService;
    private final ObjectMapper objectMapper;

    public VirtClusterPresetService(
            ObjectManager objectManager,
            ObjectTemplateService objectTemplateService,
            LabBlueprintBootstrap labBlueprintBootstrap,
            DeviceProvisioningService deviceProvisioningService,
            DriverRuntimeService driverRuntimeService,
            BindingRulesService bindingRulesService,
            BindingDependencyIndex bindingDependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            AutomationTreeService automationTreeService,
            DashboardService dashboardService,
            OperatorAppUiService operatorAppUiService,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.objectTemplateService = objectTemplateService;
        this.labBlueprintBootstrap = labBlueprintBootstrap;
        this.deviceProvisioningService = deviceProvisioningService;
        this.driverRuntimeService = driverRuntimeService;
        this.bindingRulesService = bindingRulesService;
        this.bindingDependencyIndex = bindingDependencyIndex;
        this.bindingRuleEngine = bindingRuleEngine;
        this.automationTreeService = automationTreeService;
        this.dashboardService = dashboardService;
        this.operatorAppUiService = operatorAppUiService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> install(boolean wireOperatorApp, String operatorAppId) {
        labBlueprintBootstrap.ensureLabModels();

        ensureFolder(
                "root.platform.devices",
                "virt-cluster",
                "Virtual cluster folder",
                "Reference project: 3× virtual-lab devices + hub + dashboards"
        );

        List<String> devicePaths = new ArrayList<>();
        devicePaths.add(ensureLabDevice("dev-01", "Virt cluster dev-01"));
        devicePaths.add(ensureLabDevice("dev-02", "Virt cluster dev-02"));
        devicePaths.add(ensureLabDevice("dev-03", "Virt cluster dev-03"));

        ensureHub();
        ensureHubBindings();
        String alertPath = ensureAlert();
        ensureDashboard(OVERVIEW, "Virtual cluster overview", "virtual-cluster-overview");
        ensureDashboard(DETAIL, "Virtual cluster detail", "virtual-cluster-detail");

        for (String devicePath : devicePaths) {
            try {
                driverRuntimeService.start(devicePath);
                driverRuntimeService.pollNow(devicePath);
            } catch (Exception ignored) {
                // driver may already be running
            }
        }

        String appId = operatorAppId == null || operatorAppId.isBlank() ? "platform" : operatorAppId.trim();
        if (wireOperatorApp) {
            wireOperator(appId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("folder", FOLDER);
        result.put("devices", devicePaths);
        result.put("hub", HUB);
        result.put("alert", alertPath);
        result.put("overviewDashboard", OVERVIEW);
        result.put("detailDashboard", DETAIL);
        if (wireOperatorApp) {
            result.put("operatorAppId", appId);
            result.put("operatorUrlHint", "?mode=operator&app=" + appId);
        }
        return result;
    }

    private void ensureFolder(String parentPath, String name, String displayName, String description) {
        String path = parentPath + "." + name;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(parentPath, name, ObjectType.CUSTOM, displayName, description, null);
        }
    }

    private String ensureLabDevice(String name, String displayName) {
        String path = FOLDER + "." + name;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    FOLDER,
                    name,
                    ObjectType.DEVICE,
                    displayName,
                    "",
                    LabBlueprintBootstrap.VIRTUAL_LAB_MODEL
            );
        }
        objectTemplateService.applyTemplate(path, LabBlueprintBootstrap.VIRTUAL_LAB_MODEL);
        deviceProvisioningService.provisionDriver(path, "virtual", 2000, false);

        // driverConfigJson / point maps are read-only after provision — configure via driver API only
        Map<String, String> configuration = parseFlatConfig(LAB_CONFIG);
        driverRuntimeService.configure(
                path,
                DriverBinding.of("virtual", 2000, configuration, LAB_POINT_MAP)
        );
        driverRuntimeService.setDriverAutoStart(path, true);

        for (String wave : WAVE_VARS) {
            if (objectManager.require(path).getVariable(wave).isPresent()) {
                objectManager.updateVariableHistory(path, wave, true, null, null);
            }
        }
        objectManager.persistNodeTree(path);
        return path;
    }

    private void ensureHub() {
        String path = HUB;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(
                    FOLDER,
                    "hub",
                    ObjectType.CUSTOM,
                    "Cluster hub",
                    "Aggregates member sine waves and clusterError",
                    null
            );
        }
        ensureVariable(path, "member1Sine", DOUBLE_VALUE);
        ensureVariable(path, "member2Sine", DOUBLE_VALUE);
        ensureVariable(path, "member3Sine", DOUBLE_VALUE);
        ensureVariable(path, "clusterError", BOOLEAN_VALUE);
        objectManager.persistNodeTree(path);
    }

    private void ensureHubBindings() {
        String hub = HUB;
        upsertRemoteBinding(
                hub,
                "member1-sine",
                "member1Sine",
                DEV_01,
                "sineWave",
                "read(\"" + DEV_01 + "/sineWave\")"
        );
        upsertRemoteBinding(
                hub,
                "member2-sine",
                "member2Sine",
                DEV_02,
                "sineWave",
                "read(\"" + DEV_02 + "/sineWave\")"
        );
        upsertRemoteBinding(
                hub,
                "member3-sine",
                "member3Sine",
                DEV_03,
                "sineWave",
                "read(\"" + DEV_03 + "/sineWave\")"
        );
        BindingRule clusterError = new BindingRule(
                "cluster-error",
                "cluster-error",
                true,
                10,
                BindingActivators.onLocalChange(),
                "",
                "self.member1Sine[\"value\"] > 0 && self.member2Sine[\"value\"] > 0 && self.member3Sine[\"value\"] > 0",
                new BindingTarget("clusterError", "value")
        );
        bindingRulesService.upsertRule(hub, clusterError);
        bindingDependencyIndex.rebuild(hub);
        bindingRuleEngine.runRulesForObject(hub);
    }

    private void upsertRemoteBinding(
            String hubPath,
            String id,
            String targetVariable,
            String remotePath,
            String remoteVariable,
            String expression
    ) {
        BindingRule rule = new BindingRule(
                id,
                id,
                true,
                0,
                BindingRuleKind.REACTIVE,
                BindingActivators.onRemoteChange(remotePath, remoteVariable),
                "",
                expression,
                new BindingTarget(targetVariable, "value"),
                null,
                null
        );
        bindingRulesService.upsertRule(hubPath, rule);
    }

    private String ensureAlert() {
        automationTreeService.ensurePlatformFolders();
        String name = "virt-cluster-error";
        String path = AutomationTreeService.rulePathForName(name);
        if (objectManager.tree().findByPath(path).isEmpty()) {
            automationTreeService.createAlertRule(
                    name,
                    HUB,
                    "clusterError",
                    "self.clusterError[\"value\"] == true",
                    "virtClusterError",
                    "clusterError",
                    true,
                    true,
                    0,
                    false,
                    "HIGH",
                    false,
                    null,
                    null,
                    null
            );
        } else {
            automationTreeService.updateAlertRule(
                    path,
                    name,
                    HUB,
                    "clusterError",
                    "self.clusterError[\"value\"] == true",
                    "virtClusterError",
                    "clusterError",
                    true,
                    true,
                    0,
                    false,
                    "HIGH",
                    false,
                    null,
                    null,
                    null,
                    null
            );
        }
        return path;
    }

    private void ensureDashboard(String path, String title, String template) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            String name = path.substring(path.lastIndexOf('.') + 1);
            objectManager.create(
                    "root.platform.dashboards",
                    name,
                    ObjectType.DASHBOARD,
                    title,
                    "",
                    "dashboard-v1"
            );
        }
        dashboardService.ensureDashboardStructure(path);
        dashboardService.updateTitle(path, title);
        dashboardService.applyTemplateLayout(path, template);
        dashboardService.updateRefreshInterval(path, 3000);
    }

    private void wireOperator(String appId) {
        try {
            try {
                operatorAppUiService.getUi(appId);
            } catch (IllegalArgumentException missing) {
                operatorAppUiService.createApp(appId, "Platform HMI");
            }
            Map<String, Object> alarmBar = new LinkedHashMap<>();
            alarmBar.put("enabled", true);
            alarmBar.put("position", "top");
            alarmBar.put("minLevel", "WARNING");
            alarmBar.put(
                    "rules",
                    List.of(Map.of(
                            "id", "virt-cluster-error",
                            "eventNames", List.of("virtClusterError"),
                            "title", "Virtual cluster error",
                            "minLevel", "WARNING",
                            "objectPathPrefix", AutomationTreeService.ALERT_RULES_ROOT,
                            "acknowledgeFunction", "acknowledgeAlarm"
                    ))
            );
            List<Map<String, String>> dashboards = List.of(
                    Map.of("path", OVERVIEW, "title", "Virtual cluster overview"),
                    Map.of("path", DETAIL, "title", "Virtual cluster detail")
            );
            operatorAppUiService.saveUi(
                    appId,
                    "Platform HMI",
                    OVERVIEW,
                    dashboards,
                    alarmBar
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to wire operator app: " + ex.getMessage(), ex);
        }
    }

    private void ensureVariable(String path, String name, DataSchema schema) {
        PlatformObject node = objectManager.require(path);
        if (node.getVariable(name).isEmpty()) {
            objectManager.createVariable(path, name, schema, true, false, null, false, null);
        }
    }

    private void setStringVar(String path, String name, String value) {
        PlatformObject node = objectManager.require(path);
        if (node.getVariable(name).isEmpty()) {
            objectManager.createVariable(
                    path,
                    name,
                    STRING_VALUE,
                    true,
                    true,
                    DataRecord.single(STRING_VALUE, Map.of("value", value)),
                    false,
                    null
            );
            objectManager.persistNodeTree(path);
            return;
        }
        // driverConfigJson / point maps are often read-only after provision — configure via driver API instead
        try {
            node.setVariableValue(name, DataRecord.single(STRING_VALUE, Map.of("value", value)));
            objectManager.persistNodeTree(path);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // already provisioned; DriverRuntimeService.configure holds the live config
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseFlatConfig(String json) {
        try {
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                out.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            return out;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid driver config JSON", ex);
        }
    }
}
