package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopGuardTest {

    @Test
    void hardBlocksRepeatedGetDriverHelp() {
        // Two prior get_driver_help calls → third is hard-blocked.
        List<Map<String, Object>> steps = List.of(
                step("get_driver_help"),
                step("get_driver_help")
        );
        var block = AgentLoopGuard.checkHardBlock("get_driver_help", steps);
        assertTrue(block.isPresent());
        assertTrue(block.get().blocked());
        assertFalse(block.get().stopTurn());
        assertTrue(block.get().error().contains("stop looping"));
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("create_object"));
        assertTrue(block.get().hint().contains("snmp-agent-v1"));
    }

    @Test
    void hintsExecuteAfterSingleGetDriverHelp() {
        String hint = AgentLoopGuard.continuationHint("get_driver_help", List.of(step("get_driver_help")), 96);
        assertTrue(hint.toLowerCase(Locale.ROOT).contains("stop"));
        assertTrue(hint.contains("snmp-agent-v1"));
    }

    @Test
    void warnsOnRepeatedSearchContext() {
        List<Map<String, Object>> steps = List.of(
                step("search_context"),
                step("search_context"),
                step("search_context")
        );
        String hint = AgentLoopGuard.continuationHint("search_context", steps, 96);
        assertTrue(hint.toLowerCase(Locale.ROOT).contains("search_context"));
    }

    @Test
    void warnsNearStepBudget() {
        List<Map<String, Object>> steps = List.of(
                step("list_objects"),
                step("get_object"),
                step("set_variable"),
                step("list_variables"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget"),
                step("add_dashboard_widget")
        );
        String hint = AgentLoopGuard.continuationHint("add_dashboard_widget", steps, 18);
        assertTrue(hint.contains("finish"));
    }

    @Test
    void hintsListObjectsAfterGroundTruthBlock() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(Map.of(
                "type", "tool",
                "tool", "create_object",
                "arguments", Map.of("parentPath", "root.platform.mimics", "name", "hmi"),
                "result", Map.of(
                        "status", "ERROR",
                        "error", "Cannot create under hmi: parent path was not discovered in this turn: root.platform.mimics"
                )
        ));
        String hint = AgentLoopGuard.continuationHint("create_object", steps, 96);
        assertTrue(hint.contains("list_objects parent=root.platform.mimics"));
    }

    @Test
    void hintsDiscoveryAfterRecipeSearch() {
        String hint = AgentLoopGuard.continuationHint("search_platform_recipes", List.of(step("search_platform_recipes")), 96);
        assertTrue(hint.contains("list_objects"));
        assertTrue(hint.contains("not live tree"));
    }

    @Test
    void hintsDiscoveryAfterAutomationSchema() {
        String hint = AgentLoopGuard.continuationHint("get_automation_schema", List.of(step("get_automation_schema")), 96);
        assertTrue(hint.contains("list_mixin_blueprints"));
    }

    @Test
    void hintsDrillDownAfterListRoot() {
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "list_objects",
                "arguments", Map.of("parent", "root"),
                "result", Map.of("status", "OK", "parent", "root", "objects", List.of())
        ));
        String hint = AgentLoopGuard.continuationHint("list_objects", steps, 96);
        assertTrue(hint.contains("root.platform"));
    }

    @Test
    void hintsWorkflowOrderAfterSaveBpmnNotFound() {
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "save_workflow_bpmn",
                "arguments", Map.of("path", "root.platform.workflows.hydraulic-shock"),
                "result", Map.of("status", "ERROR", "error", "Object not found: root.platform.workflows.hydraulic-shock")
        ));
        String hint = AgentLoopGuard.continuationHint("save_workflow_bpmn", steps, 96);
        assertTrue(hint.contains("create_object"));
    }

    @Test
    void hintsSetDashboardLayoutAfterFirstAddWidget() {
        String dash = "root.platform.dashboards.virt-cluster-dashboard";
        List<Map<String, Object>> steps = List.of(widgetStep(dash));
        String hint = AgentLoopGuard.continuationHint("add_dashboard_widget", steps, 96);
        assertTrue(hint.toLowerCase(Locale.ROOT).contains("set_dashboard_layout"));
        assertTrue(hint.toLowerCase(Locale.ROOT).contains("add_dashboard_widget"));
    }

    @Test
    void stopsTurnWhenEnableAgentToolPackMissingPackTwice() {
        List<Map<String, Object>> steps = List.of(
                errorStep("enable_agent_tool_pack", "REQUIRED_ARG_MISSING at pack"),
                errorStep("enable_agent_tool_pack", "REQUIRED_ARG_MISSING at pack")
        );
        var block = AgentLoopGuard.checkHardBlock(
                "enable_agent_tool_pack",
                Map.of(),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().blocked());
        assertTrue(block.get().stopTurn());
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("pack"));
    }

    @Test
    void softBlocksThirdAddDashboardWidgetOnSamePathWithoutStoppingTurn() {
        String dash = "root.platform.dashboards.virt-cluster-dashboard";
        List<Map<String, Object>> steps = List.of(
                widgetStep(dash),
                widgetStep(dash)
        );
        var block = AgentLoopGuard.checkHardBlock(
                "add_dashboard_widget",
                Map.of("path", dash, "widget", Map.of("id", "kpi-3")),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().blocked());
        assertFalse(block.get().stopTurn());
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("set_dashboard_layout"));
    }

    @Test
    void hardStopsTurnAfterManyAddDashboardWidgetOnSamePath() {
        String dash = "root.platform.dashboards.virt-cluster-dashboard";
        List<Map<String, Object>> steps = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            steps.add(widgetStep(dash));
        }
        var block = AgentLoopGuard.checkHardBlock(
                "add_dashboard_widget",
                Map.of("path", dash, "widget", Map.of("id", "kpi-11")),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().blocked());
        assertTrue(block.get().stopTurn());
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("set_dashboard_layout"));
    }

    @Test
    void doesNotHardBlockAddWidgetOnDifferentDashboards() {
        List<Map<String, Object>> steps = List.of(
                widgetStep("root.platform.dashboards.a"),
                widgetStep("root.platform.dashboards.b")
        );
        var block = AgentLoopGuard.checkHardBlock(
                "add_dashboard_widget",
                Map.of("path", "root.platform.dashboards.c"),
                steps
        );
        assertTrue(block.isEmpty());
    }

    @Test
    void softBlocksThirdAddMimicElementsOnSamePathWithoutStoppingTurn() {
        String mimic = "root.platform.mimics.plant";
        List<Map<String, Object>> steps = List.of(
                mimicStep(mimic),
                mimicStep(mimic)
        );
        var block = AgentLoopGuard.checkHardBlock(
                "add_mimic_elements",
                Map.of("path", mimic),
                steps
        );
        assertTrue(block.isPresent());
        assertFalse(block.get().stopTurn());
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("save_mimic_diagram"));
    }

    @Test
    void hardBlocksRepeatedListObjectsOnSameParent() {
        String parent = "root.platform.devices";
        List<Map<String, Object>> steps = List.of(
                listStep(parent),
                listStep(parent),
                listStep(parent)
        );
        var block = AgentLoopGuard.checkHardBlock(
                "list_objects",
                Map.of("parent", parent),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().stopTurn());
    }

    @Test
    void allowsGetWidgetCatalogForDistinctTypes() {
        List<Map<String, Object>> steps = List.of(
                widgetCatalogStep("chart"),
                widgetCatalogStep("function-form"),
                widgetCatalogStep("svg-widget"),
                widgetCatalogStep("pie-chart")
        );
        var block = AgentLoopGuard.checkHardBlock(
                "get_widget_catalog",
                Map.of("type", "dashboard-link"),
                steps
        );
        assertTrue(block.isEmpty());
    }

    @Test
    void hardBlocksRepeatedGetWidgetCatalogSameType() {
        List<Map<String, Object>> steps = List.of(
                widgetCatalogStep("pie-chart"),
                widgetCatalogStep("pie-chart")
        );
        var block = AgentLoopGuard.checkHardBlock(
                "get_widget_catalog",
                Map.of("type", "pie-chart"),
                steps
        );
        assertTrue(block.isPresent());
        assertFalse(block.get().stopTurn());
        assertTrue(block.get().error().contains("same type"));
    }

    @Test
    void allowsRepeatedSetDashboardLayoutAfterWidgetWork() {
        String dash = "root.platform.dashboards.vd-dashboard";
        List<Map<String, Object>> steps = List.of(
                layoutStep(dash),
                widgetStep(dash),
                widgetStep(dash),
                layoutStep(dash)
        );
        var block = AgentLoopGuard.checkHardBlock(
                "set_dashboard_layout",
                Map.of("path", dash),
                steps
        );
        assertTrue(block.isEmpty());
    }

    @Test
    void hardBlocksConsecutiveSetDashboardLayoutSpam() {
        String dash = "root.platform.dashboards.virtual-devices-dashboard";
        List<Map<String, Object>> steps = List.of(
                layoutStep(dash),
                layoutStep(dash)
        );
        var block = AgentLoopGuard.checkHardBlock(
                "set_dashboard_layout",
                Map.of("path", dash),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().stopTurn());
        assertTrue(block.get().error().contains("consecutive"));
    }

    @Test
    void allowsManyLayoutsAcrossTurnWhenNotConsecutive() {
        String dash = "root.platform.dashboards.virtual-devices-dashboard";
        List<Map<String, Object>> steps = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            steps.add(layoutStep(dash));
            steps.add(widgetStep(dash));
        }
        var block = AgentLoopGuard.checkHardBlock(
                "set_dashboard_layout",
                Map.of("path", dash),
                steps
        );
        assertTrue(block.isEmpty());
    }

    @Test
    void doesNotTreatDifferentBindingExpressionsAsIdentical() {
        String path = "root.platform.devices.vd";
        List<Map<String, Object>> steps = List.of(
                bindingStep(path, "sumRecordField(self.table.rows, value)"),
                bindingStep(path, "self.table.rows.map(r | r.value).sum()"),
                bindingStep(path, "sum(self.table.rows[*].value)"),
                bindingStep(path, "self.table.rows.reduce(0, (acc, r) | acc + r.value)")
        );
        var block = AgentLoopGuard.checkHardBlock(
                "create_binding_rule",
                Map.of(
                        "path", path,
                        "id", "table-sum",
                        "expression", "sumRecordField(table, int)",
                        "targetVariable", "tableIntSum"
                ),
                steps
        );
        assertTrue(block.isEmpty());
    }

    @Test
    void hardBlocksRepeatedConfigureReportAfterOk() {
        String path = "root.platform.reports.report-table-all";
        List<Map<String, Object>> steps = List.of(
                reportStep(path),
                reportStep(path)
        );
        var block = AgentLoopGuard.checkHardBlock(
                "configure_report",
                Map.of("path", path),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().stopTurn());
        assertTrue(block.get().error().contains("already succeeded"));
    }

    @Test
    void hardBlocksCreateObjectAfterReuseFlood() {
        String parent = "root.platform.mimics";
        String name = "mimic-button-fan-svg";
        List<Map<String, Object>> steps = List.of(
                createObjectStep(parent, name, "MIMIC", false),
                createObjectStep(parent, name, "MIMIC", true)
        );
        var block = AgentLoopGuard.checkHardBlock(
                "create_object",
                Map.of("parentPath", parent, "name", name, "type", "MIMIC", "displayName", name),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().stopTurn());
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("save_mimic_diagram"));
    }

    @Test
    void hardBlocksIdenticalSetVariableSafetyNet() {
        String path = "root.platform.devices.demo";
        List<Map<String, Object>> steps = List.of(
                setVarStep(path, "x"),
                setVarStep(path, "x"),
                setVarStep(path, "x"),
                setVarStep(path, "x"),
                setVarStep(path, "x")
        );
        var block = AgentLoopGuard.checkHardBlock(
                "set_variable",
                Map.of("path", path, "name", "x", "value", "6"),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().stopTurn());
    }

    @Test
    void hardBlocksRepeatedIdenticalBindingArgError() {
        List<Map<String, Object>> steps = List.of(
                errorStep("create_binding_rule", "path, id, expression are required"),
                errorStep("create_binding_rule", "path, id, expression are required")
        );
        var block = AgentLoopGuard.checkHardBlock("create_binding_rule", Map.of(), steps);
        assertTrue(block.isPresent());
        assertFalse(block.get().stopTurn());
        assertTrue(block.get().hint().contains("path, id, expression"));
    }

    @Test
    void createVariableDoubleTypeErrorKeepsTurnOpen() {
        List<Map<String, Object>> steps = List.of(
                errorStep("create_variable", "value must be double"),
                errorStep("create_variable", "value must be double")
        );
        var block = AgentLoopGuard.checkHardBlock("create_variable", Map.of(), steps);
        assertTrue(block.isPresent());
        assertFalse(block.get().stopTurn());
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("0.0")
                || block.get().hint().toLowerCase(Locale.ROOT).contains("initialvalue"));
    }

    @Test
    void missingEventSchemaArgsBlockKeepsTurnOpenWithHint() {
        List<Map<String, Object>> steps = List.of(
                errorStep("get_event_schema", "objectPath and eventName are required"),
                errorStep("get_event_schema", "objectPath and eventName are required")
        );
        var block = AgentLoopGuard.checkHardBlock("get_event_schema", Map.of(), steps);
        assertTrue(block.isPresent());
        assertFalse(block.get().stopTurn());
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("objectpath"));
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("eventname"));
    }

    @Test
    void missingBlueprintArgsBlockKeepsTurnOpenWithHint() {
        List<Map<String, Object>> steps = List.of(
                errorStep("get_object_blueprint", "blueprintName or blueprintId required; use list_mixin_blueprints"),
                errorStep("get_object_blueprint", "blueprintName or blueprintId required; use list_mixin_blueprints")
        );
        var block = AgentLoopGuard.checkHardBlock("get_object_blueprint", Map.of(), steps);
        assertTrue(block.isPresent());
        assertFalse(block.get().stopTurn());
        assertTrue(block.get().hint().contains("blueprintName"));
    }

    @Test
    void allowsDistinctSetVariableNamesOnSamePath() {
        String path = "root.platform.devices.demo";
        List<Map<String, Object>> steps = List.of(
                setVarStep(path, "a"),
                setVarStep(path, "b"),
                setVarStep(path, "c"),
                setVarStep(path, "d"),
                setVarStep(path, "e")
        );
        var block = AgentLoopGuard.checkHardBlock(
                "set_variable",
                Map.of("path", path, "name", "f", "value", "1"),
                steps
        );
        assertTrue(block.isEmpty());
    }

    @Test
    void softBlocksRepeatedConfigureAlertTautologyWithoutStoppingTurn() {
        String tautologyError = "Formal verification rejected condition: satisfiable witness: "
                + "Condition is satisfiable. (The expression is satisfiable unconditionally, "
                + "regardless of input state); tautology: expression is always true "
                + "(condition never discriminates)";
        List<Map<String, Object>> steps = List.of(
                errorStep("configure_alert", tautologyError),
                errorStep("configure_alert", tautologyError)
        );
        var block = AgentLoopGuard.checkHardBlock(
                "configure_alert",
                Map.of(
                        "name", "demo-alert",
                        "targetObjectPath", "root.platform.devices.virt-device-1",
                        "watchVariable", "sineWave",
                        "conditionExpr", "true",
                        "eventName", "demo"
                ),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().blocked());
        assertFalse(block.get().stopTurn());
        assertTrue(block.get().hint().toLowerCase(Locale.ROOT).contains("discriminat"));
    }

    @Test
    void hardBlocksListVariablesGetObjectOscillation() {
        String cluster = "root.platform.devices.virt-cluster";
        String alert = "root.platform.alert-rules.virt-cluster-error-alert";
        List<Map<String, Object>> steps = List.of(
                toolOnPath("list_variables", cluster),
                toolOnPath("get_object", alert),
                toolOnPath("list_variables", cluster),
                toolOnPath("get_object", alert),
                toolOnPath("list_variables", cluster)
        );
        var block = AgentLoopGuard.checkHardBlock(
                "get_object",
                Map.of("path", alert),
                steps
        );
        assertTrue(block.isPresent());
        assertTrue(block.get().stopTurn());
        assertTrue(block.get().error().toLowerCase(Locale.ROOT).contains("oscillat"));
    }

    @Test
    void allowsCreateObjectDashboardThenSetLayoutOscillation() {
        // Course builds many dashboards: create_object ↔ set_dashboard_layout on new paths.
        List<Map<String, Object>> steps = List.of(
                createDashboardStep("sine-history-dashboard"),
                layoutStep("root.platform.dashboards.sine-history-dashboard"),
                createDashboardStep("table-piechart-dashboard"),
                layoutStep("root.platform.dashboards.table-piechart-dashboard"),
                createDashboardStep("button-dashboard"),
                layoutStep("root.platform.dashboards.button-dashboard"),
                createDashboardStep("event-grid-dashboard")
        );
        var block = AgentLoopGuard.checkHardBlock(
                "set_dashboard_layout",
                Map.of("path", "root.platform.dashboards.event-grid-dashboard"),
                steps
        );
        assertTrue(block.isEmpty());
    }

    @Test
    void doesNotBlockShortAlternation() {
        List<Map<String, Object>> steps = List.of(
                toolOnPath("list_variables", "root.a"),
                toolOnPath("get_object", "root.b"),
                toolOnPath("list_variables", "root.a")
        );
        var block = AgentLoopGuard.checkHardBlock(
                "get_object",
                Map.of("path", "root.b"),
                steps
        );
        assertTrue(block.isEmpty());
    }

    @Test
    void hintsConfigureOperatorUiAfterLayoutVariableMiss() {
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "set_variable",
                "arguments", Map.of("path", "root.platform.operator-apps.x", "name", "layout"),
                "result", Map.of("status", "ERROR", "error", "Variable not found: layout")
        ));
        String hint = AgentLoopGuard.continuationHint("set_variable", steps, 96);
        assertTrue(hint.toLowerCase(Locale.ROOT).contains("configure_operator_ui"));
    }

    private static Map<String, Object> step(String tool) {
        return Map.of("type", "tool", "tool", tool);
    }

    private static Map<String, Object> layoutStep(String path) {
        return Map.of(
                "type", "tool",
                "tool", "set_dashboard_layout",
                "arguments", Map.of("path", path),
                "result", Map.of("status", "OK")
        );
    }

    private static Map<String, Object> reportStep(String path) {
        return Map.of(
                "type", "tool",
                "tool", "configure_report",
                "arguments", Map.of("path", path),
                "result", Map.of("status", "OK")
        );
    }

    private static Map<String, Object> bindingStep(String path, String expression) {
        return Map.of(
                "type", "tool",
                "tool", "create_binding_rule",
                "arguments", Map.of(
                        "path", path,
                        "id", "table-sum",
                        "expression", expression,
                        "targetVariable", "tableIntSum"
                ),
                "result", Map.of("status", "ERROR", "error", "Invalid expression: " + expression)
        );
    }

    private static Map<String, Object> widgetCatalogStep(String type) {
        return Map.of(
                "type", "tool",
                "tool", "get_widget_catalog",
                "arguments", Map.of("type", type),
                "result", Map.of("status", "OK")
        );
    }

    private static Map<String, Object> createObjectStep(
            String parent,
            String name,
            String objectType,
            boolean reused
    ) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", "OK");
        result.put("path", parent + "." + name);
        if (reused) {
            result.put("reused", true);
        }
        return Map.of(
                "type", "tool",
                "tool", "create_object",
                "arguments", Map.of(
                        "parentPath", parent,
                        "name", name,
                        "type", objectType,
                        "displayName", name
                ),
                "result", result
        );
    }

    private static Map<String, Object> widgetStep(String path) {
        return Map.of(
                "type", "tool",
                "tool", "add_dashboard_widget",
                "arguments", Map.of("path", path)
        );
    }

    private static Map<String, Object> layoutStep(String path) {
        return Map.of(
                "type", "tool",
                "tool", "set_dashboard_layout",
                "arguments", Map.of("path", path),
                "result", Map.of("status", "OK")
        );
    }

    private static Map<String, Object> createDashboardStep(String name) {
        return Map.of(
                "type", "tool",
                "tool", "create_object",
                "arguments", Map.of(
                        "parentPath", "root.platform.dashboards",
                        "name", name,
                        "type", "DASHBOARD"
                ),
                "result", Map.of("status", "OK", "path", "root.platform.dashboards." + name)
        );
    }

    private static Map<String, Object> mimicStep(String path) {
        return Map.of(
                "type", "tool",
                "tool", "add_mimic_elements",
                "arguments", Map.of("path", path)
        );
    }

    private static Map<String, Object> listStep(String parent) {
        return Map.of(
                "type", "tool",
                "tool", "list_objects",
                "arguments", Map.of("parent", parent)
        );
    }

    private static Map<String, Object> setVarStep(String path, String name) {
        return Map.of(
                "type", "tool",
                "tool", "set_variable",
                "arguments", Map.of("path", path, "name", name, "value", "1")
        );
    }

    private static Map<String, Object> errorStep(String tool, String error) {
        return Map.of(
                "type", "tool",
                "tool", tool,
                "arguments", Map.of(),
                "result", Map.of("status", "ERROR", "error", error)
        );
    }

    private static Map<String, Object> toolOnPath(String tool, String path) {
        return Map.of(
                "type", "tool",
                "tool", tool,
                "arguments", Map.of("path", path),
                "result", Map.of("status", "OK")
        );
    }
}
