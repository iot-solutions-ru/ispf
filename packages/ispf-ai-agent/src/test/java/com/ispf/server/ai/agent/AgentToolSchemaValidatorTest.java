package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolSchemaValidatorTest {

    @Test
    void acceptsValidCreateObjectArgs() {
        var schema = AgentToolInputSchemas.forTool("create_object");
        var result = AgentToolSchemaValidator.validate(schema, Map.of(
                "parentPath", "root.platform.devices",
                "name", "pump-1",
                "type", "DEVICE",
                "displayName", "Pump 1"
        ));
        assertThat(result).isEmpty();
    }

    @Test
    void rejectsMissingRequiredArg() {
        var schema = AgentToolInputSchemas.forTool("get_object");
        var result = AgentToolSchemaValidator.validate(schema, Map.of());
        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("REQUIRED_ARG_MISSING");
        assertThat(result.get().path()).isEqualTo("path");
        assertThat(result.get().toErrorResult()).containsEntry("docRef", AgentToolSchemaValidator.DOC_REF);
    }

    @Test
    void widgetSchemaRejectsUnknownKeysAndAcceptsCatalogFields() {
        var schema = AgentToolInputSchemas.forTool("add_dashboard_widget");
        assertThat(schema.get("additionalProperties")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> widget = (Map<String, Object>) properties.get("widget");
        assertThat(widget.get("additionalProperties")).isEqualTo(false);
        assertThat(widget.get("required")).asList().contains("type");

        assertThat(AgentToolSchemaValidator.validate(schema, Map.of(
                "path", "root.platform.dashboards.main",
                "widget", Map.of(
                        "type", "object-table",
                        "columnsJson", "[{\"variable\":\"name\"}]",
                        "parentPath", "root.platform.devices"
                )
        ))).isEmpty();

        var unknown = AgentToolSchemaValidator.validate(schema, Map.of(
                "path", "root.platform.dashboards.main",
                "widget", Map.of("type", "value", "notAField", "x")
        ));
        assertThat(unknown).isPresent();
        assertThat(unknown.get().code()).isEqualTo("UNKNOWN_ARG");
        assertThat(unknown.get().path()).isEqualTo("widget.notAField");

        var badType = AgentToolSchemaValidator.validate(schema, Map.of(
                "path", "root.platform.dashboards.main",
                "widget", Map.of("type", "not-a-widget")
        ));
        assertThat(badType).isPresent();
        assertThat(badType.get().code()).isEqualTo("ARG_ENUM_MISMATCH");
        assertThat(badType.get().path()).isEqualTo("widget.type");

        Map<String, Object> typed = new java.util.LinkedHashMap<>();
        typed.put("type", "report");
        typed.put("x", 0);
        typed.put("y", 1);
        typed.put("w", 4);
        typed.put("h", 3);
        typed.put("minValue", 1.5);
        typed.put("showGrid", true);
        typed.put("editable", false);
        typed.put("ref", "root.platform.devices.dev1/temp");
        typed.put("buttonsJson", "[]");
        typed.put("selectable", true);
        typed.put("filterable", true);
        typed.put("workflowPath", "root.platform.workflows.ack");
        typed.put("wizardStepsJson", "[]");
        typed.put("alarmMode", false);
        typed.put("markerRulesJson", "[]");
        typed.put("analyticsTemplateId", "tpl");
        assertThat(AgentToolSchemaValidator.validate(schema, Map.of(
                "path", "root.platform.dashboards.main",
                "widget", typed
        ))).isEmpty();

        Map<String, Object> badGrid = new java.util.LinkedHashMap<>();
        badGrid.put("type", "value");
        badGrid.put("x", "abc");
        var badNumber = AgentToolSchemaValidator.validate(schema, Map.of(
                "path", "root.platform.dashboards.main",
                "widget", badGrid
        ));
        assertThat(badNumber).isPresent();
        assertThat(badNumber.get().code()).isEqualTo("ARG_TYPE_MISMATCH");
        assertThat(badNumber.get().path()).isEqualTo("widget.x");
    }

    @Test
    void rejectsInvalidEnum() {
        var schema = AgentToolInputSchemas.forTool("driver_control");
        var result = AgentToolSchemaValidator.validate(schema, Map.of(
                "devicePath", "root.platform.devices.x",
                "action", "explode"
        ));
        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("ARG_ENUM_MISMATCH");
    }

    @Test
    void openStubDetection() {
        assertThat(AgentToolSchemaValidator.isOpenStub(Map.of(
                "type", "object",
                "additionalProperties", true
        ))).isTrue();
        assertThat(AgentToolSchemaValidator.isOpenStub(AgentToolInputSchemas.forTool("list_objects"))).isFalse();
        assertThat(AgentToolSchemaValidator.isOpenStub(AgentToolSchemas.emptyObject())).isFalse();
    }

    @Test
    void ignoresBlankOptionalEnum() {
        var schema = AgentToolSchemas.objectSchema(
                AgentToolSchemas.props(
                        "type", AgentToolSchemas.enumProp("t", List.of("a", "b"))
                ),
                List.of(),
                true
        );
        assertThat(AgentToolSchemaValidator.validate(schema, Map.of("type", ""))).isEmpty();
    }
}
