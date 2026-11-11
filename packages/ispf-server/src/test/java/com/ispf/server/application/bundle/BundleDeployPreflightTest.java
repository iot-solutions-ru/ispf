package com.ispf.server.application.bundle;

import com.ispf.core.object.ObjectType;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleBlueprint;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleDashboard;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleMigration;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleObject;
import com.ispf.server.application.bundle.ApplicationBundleDeployService.BundleWorkflow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleDeployPreflightTest {

    @Test
    void acceptsChildWhoseParentIsCreatedEarlierInTheManifest() {
        List<String> errors = BundleDeployPreflight.dryRun(
                List.of(
                        object("root.platform.devices", "gateway", "DEVICE"),
                        object("root.platform.devices.gateway", "child", "DEVICE")
                ),
                List.of(),
                List.of(new BundleMigration("init", "CREATE TABLE demo_item (id INT)")),
                "",
                Set.of("root.platform.devices")::contains
        );
        assertEquals(List.of(), errors);
    }

    @Test
    void rejectsMissingParentUnknownTypeDuplicatePathAndReservedSql() {
        List<String> errors = BundleDeployPreflight.dryRun(
                List.of(
                        object("root.missing", "orphan", "DEVICE"),
                        object("root.platform.devices", "bad", "NOT_A_TYPE"),
                        object("root.platform.devices", "same", "DEVICE"),
                        object("root.platform.devices", "same", "DEVICE"),
                        object("root.platform.devices", "a.b", "DEVICE")
                ),
                List.of(),
                List.of(new BundleMigration("init", "CREATE TABLE applications (id INT)")),
                "",
                Set.of("root.platform.devices")::contains
        );
        assertTrue(errors.stream().anyMatch(item -> item.contains("parent does not exist")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("unknown type")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("duplicate path")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("must not contain")));
        assertTrue(errors.stream().anyMatch(item -> item.contains("reserved platform table")));
    }

    @Test
    void acceptsDeviceChildUnderSingletonHubAndExplicitSingletonObjectEntry() {
        BundleBlueprint hub = new BundleBlueprint(
                "plant-hub",
                "",
                BlueprintType.SINGLETON,
                ObjectType.CUSTOM,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        List<String> errors = BundleDeployPreflight.dryRun(
                List.of(
                        object("root.platform.singleton-blueprints", "plant-hub", "CUSTOM"),
                        object("root.platform.singleton-blueprints.plant-hub", "sensor", "DEVICE")
                ),
                List.of(hub),
                List.of(),
                "",
                Set.of("root.platform.singleton-blueprints")::contains
        );
        assertEquals(List.of(), errors);
    }

    @Test
    void rejectsSingletonBlueprintWithMissingCustomParent() {
        BundleBlueprint hub = new BundleBlueprint(
                "plant-hub",
                "",
                BlueprintType.SINGLETON,
                ObjectType.CUSTOM,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("singletonInstancePath", "root.missing.folder.plant-hub")
        );
        List<String> errors = BundleDeployPreflight.dryRun(
                List.of(),
                List.of(hub),
                List.of(),
                "",
                Set.of("root.platform.singleton-blueprints")::contains
        );
        assertTrue(errors.stream().anyMatch(item ->
                item.contains("singletonInstancePath parent does not exist: root.missing.folder")));
    }

    @Test
    void validatesDashboardAndWorkflowLeafParentsAndPlannedRegistrationPaths() {
        List<String> okErrors = BundleDeployPreflight.dryRun(
                List.of(object("root.platform.applications.demo", "folder", "CUSTOM")),
                List.of(),
                List.of(new BundleDashboard("root.platform.dashboards.main", "Main", "{}", 5000)),
                List.of(new BundleWorkflow(
                        "root.platform.workflows.flow1", null, "DRAFT", null, "Flow 1", null, null, null, null, null
                )),
                List.of(),
                "",
                List.of("root.platform.applications.demo", "root.platform.data-sources.demo"),
                Set.of(
                        "root.platform.applications",
                        "root.platform.data-sources",
                        "root.platform.dashboards",
                        "root.platform.workflows"
                )::contains
        );
        assertEquals(List.of(), okErrors);

        List<String> badErrors = BundleDeployPreflight.dryRun(
                List.of(),
                List.of(),
                List.of(new BundleDashboard("root.no_such_dash_parent.main", "Main", "{}", 5000)),
                List.of(new BundleWorkflow(
                        "root.no_such_wf_parent.flow1", null, "DRAFT", null, "Flow 1", null, null, null, null, null
                )),
                List.of(),
                "",
                List.of(),
                Set.of("root.platform")::contains
        );
        assertTrue(badErrors.stream().anyMatch(item ->
                item.contains("dryRun:dashboard:root.no_such_dash_parent.main: parent does not exist")));
        assertTrue(badErrors.stream().anyMatch(item ->
                item.contains("dryRun:workflow:root.no_such_wf_parent.flow1: parent does not exist")));
    }

    private static BundleObject object(String parent, String name, String type) {
        return new BundleObject(parent, name, type, name, "", null, null);
    }
}
