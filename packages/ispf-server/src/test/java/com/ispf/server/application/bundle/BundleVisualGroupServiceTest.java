package com.ispf.server.application.bundle;

import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.report.ReportService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BundleVisualGroupServiceTest {

    @Test
    void collectMembersByCatalogFromManifestSections() {
        ApplicationBundleDeployService.BundleManifest manifest = sampleManifest();

        Map<String, List<String>> grouped = BundleVisualGroupService.collectMembersByCatalog("demo-app", manifest);

        assertThat(grouped.get("root.platform.devices")).contains("root.platform.devices.demo-device");
        assertThat(grouped.get("root.platform.dashboards")).contains("root.platform.dashboards.demo-dash");
        assertThat(grouped.get("root.platform.workflows")).contains("root.platform.workflows.demo-flow");
        assertThat(grouped.get("root.platform.reports")).contains(ReportService.reportPath("demo-report"));
        assertThat(grouped.get("root.platform.alert-rules"))
                .contains(AutomationTreeService.rulePathForName("Demo alert"));
        assertThat(grouped.get("root.platform.correlators"))
                .contains(AutomationTreeService.correlatorPathForName("Demo correlator"));
        assertThat(grouped.get("root.platform.schedules")).contains("root.platform.schedules.demo-tick");
        assertThat(grouped.get("root.platform.bindings")).contains(
                SqlBindingObjectService.BINDINGS_ROOT + "."
                        + SqlBindingObjectService.sanitizeNodeName("root-platform-devices-demo-device-sqlVar"));
        assertThat(grouped.get("root.platform.data-sources")).contains("root.platform.data-sources.demo-app");
        assertThat(grouped.get("root.platform.applications"))
                .contains(ApplicationBundleDeployService.applicationTreePath("demo-app"));
        assertThat(grouped.get("root.platform.operator-apps"))
                .contains(ApplicationBundleDeployService.operatorAppTreePath("demo-app"));
    }

    @Test
    void groupPathLivesInsideCatalogFolder() {
        assertThat(BundleVisualGroupService.groupPathForCatalogAndApp("root.platform.devices", "mes-ogp-events"))
                .isEqualTo("root.platform.devices.bundle-mes-ogp-events");
    }

    private static ApplicationBundleDeployService.BundleManifest sampleManifest() {
        return new ApplicationBundleDeployService.BundleManifest(
                "1.0.0",
                "Demo App",
                "demo_",
                null,
                List.of(new ApplicationBundleDeployService.BundleObject(
                        "root.platform.devices",
                        "demo-device",
                        "DEVICE",
                        "Demo Device",
                        "",
                        null
                )),
                List.of(new ApplicationBundleDeployService.BundleDashboard(
                        "root.platform.dashboards.demo-dash",
                        "Demo Dash",
                        null,
                        null
                )),
                List.of(new ApplicationBundleDeployService.BundleWorkflow(
                        "root.platform.workflows.demo-flow",
                        null,
                        null,
                        null
                )),
                null,
                null,
                List.of(new ApplicationBundleDeployService.BundleFunction(
                        "root.platform.devices.demo-device",
                        "demoFn",
                        "1",
                        null,
                        null
                )),
                List.of(new ApplicationBundleDeployService.BundleSqlBinding(
                        "root.platform.devices.demo-device",
                        "sqlVar",
                        "select 1",
                        "manual",
                        null,
                        "value",
                        null,
                        null,
                        true
                )),
                List.of(new ApplicationBundleDeployService.BundleReport(
                        "demo-report",
                        "Demo Report",
                        "",
                        "sql",
                        null,
                        null,
                        "select 1",
                        null,
                        null,
                        null
                )),
                List.of(new ApplicationBundleDeployService.BundleAlertRule(
                        "Demo alert",
                        "root.platform.devices.demo-device",
                        "temp",
                        "true",
                        "demoEvent",
                        null,
                        true,
                        true,
                        0,
                        false
                )),
                List.of(new ApplicationBundleDeployService.BundleCorrelator(
                        "Demo correlator",
                        "root.platform.devices.demo-device",
                        "COUNT",
                        "demoEvent",
                        null,
                        60,
                        1,
                        120,
                        0,
                        "RUN_WORKFLOW",
                        "root.platform.workflows.demo-flow",
                        null,
                        true
                )),
                List.of(new ApplicationBundleDeployService.BundleSchedule(
                        "demo-tick",
                        true,
                        60_000L,
                        "invoke_function",
                        java.util.Map.of(
                                "objectPath", "root.platform.devices.demo-device",
                                "functionName", "demoFn"
                        )
                )),
                List.of(new ApplicationBundleDeployService.BundleEvent(
                        "demoEvent",
                        List.of("operator"),
                        null
                )),
                null,
                null,
                null,
                null,
                null
        );
    }
}
