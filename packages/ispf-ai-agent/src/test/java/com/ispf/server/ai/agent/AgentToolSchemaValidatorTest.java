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
