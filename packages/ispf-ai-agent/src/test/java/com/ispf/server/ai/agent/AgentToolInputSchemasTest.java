package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AgentToolInputSchemasTest {

    @Autowired
    private PlatformAgentToolRegistry toolRegistry;

    @Test
    void catalogCoversEveryRegisteredToolWithNonStubSchema() {
        assertThat(AgentToolInputSchemas.catalogSize()).isGreaterThanOrEqualTo(100);
        var catalog = toolRegistry.toolCatalog();
        assertThat(catalog).isNotEmpty();
        int stubCount = 0;
        for (Map<String, Object> row : catalog) {
            String name = String.valueOf(row.get("name"));
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) row.get("inputSchema");
            assertThat(schema).as("inputSchema for %s", name).isNotNull();
            assertThat(schema.get("type")).as("type for %s", name).isEqualTo("object");
            assertThat(AgentToolInputSchemas.hasCatalogEntry(name) || !AgentToolSchemaValidator.isOpenStub(schema))
                    .as("tool %s must have a catalog entry or a non-stub schema", name)
                    .isTrue();
            if (AgentToolSchemaValidator.isOpenStub(schema)) {
                stubCount++;
            }
        }
        double stubRate = stubCount / (double) catalog.size();
        assertThat(stubRate)
                .as("open-stub rate should stay under 5%% (ADR-0051); stubs=%s/%s", stubCount, catalog.size())
                .isLessThan(0.05);
    }

    @Test
    void createObjectSchemaExposesObjectTypeEnum() {
        Map<String, Object> schema = AgentToolInputSchemas.forTool("create_object");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> type = (Map<String, Object>) properties.get("type");
        assertThat(type.get("enum")).asList().contains("DEVICE", "DASHBOARD", "WORKFLOW");
        assertThat(schema.get("required")).asList().contains("parentPath", "name", "type", "displayName");
    }

    @Test
    void createVariableAndBindingSchemasMatchToolContracts() {
        Map<String, Object> variable = AgentToolInputSchemas.forTool("create_variable");
        assertThat(variable.get("required")).asList().contains("path", "name", "valueType");
        @SuppressWarnings("unchecked")
        Map<String, Object> variableProps = (Map<String, Object>) variable.get("properties");
        assertThat(variableProps).containsKeys("valueType", "path", "name");
        assertThat(variableProps).doesNotContainKey("dataType");

        Map<String, Object> binding = AgentToolInputSchemas.forTool("create_binding_rule");
        assertThat(binding.get("required")).asList().contains("path", "id", "expression");
        @SuppressWarnings("unchecked")
        Map<String, Object> bindingProps = (Map<String, Object>) binding.get("properties");
        assertThat(bindingProps).containsKeys("id", "expression", "targetVariable");
        assertThat(bindingProps).doesNotContainKey("ruleJson");

        Map<String, Object> operatorUi = AgentToolInputSchemas.forTool("configure_operator_ui");
        assertThat(operatorUi.get("required")).asList().contains("title", "defaultDashboard", "dashboards");

        Map<String, Object> eventSchema = AgentToolInputSchemas.forTool("get_event_schema");
        assertThat(eventSchema.get("required")).asList().contains("objectPath", "eventName");
        @SuppressWarnings("unchecked")
        Map<String, Object> eventProps = (Map<String, Object>) eventSchema.get("properties");
        assertThat(eventProps).containsKeys("objectPath", "eventName");
        assertThat(eventProps).doesNotContainKey("eventType");

        Map<String, Object> blueprint = AgentToolInputSchemas.forTool("get_object_blueprint");
        @SuppressWarnings("unchecked")
        Map<String, Object> blueprintProps = (Map<String, Object>) blueprint.get("properties");
        assertThat(blueprintProps).containsKeys("blueprintName", "blueprintId");
        assertThat(blueprintProps).doesNotContainKeys("id", "name");

        Map<String, Object> mimicSymbols = AgentToolInputSchemas.forTool("list_mimic_symbols");
        @SuppressWarnings("unchecked")
        Map<String, Object> mimicProps = (Map<String, Object>) mimicSymbols.get("properties");
        assertThat(mimicProps).containsKey("category");
    }

    @Test
    void executeRejectsInvalidArgsBeforeHandler() {
        Map<String, Object> result = null;
        try {
            result = toolRegistry.execute(
                    "get_object",
                    Map.of(),
                    new AgentContext("test", null, new AgentRunState())
            );
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        assertThat(result.get("status")).isEqualTo("ERROR");
        assertThat(result.get("code")).isEqualTo("REQUIRED_ARG_MISSING");
        assertThat(result.get("docRef")).isEqualTo(AgentToolSchemaValidator.DOC_REF);
    }
}
