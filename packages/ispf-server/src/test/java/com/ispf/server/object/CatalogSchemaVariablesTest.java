package com.ispf.server.object;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.plugin.blueprint.BlueprintVariableDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSchemaVariablesTest {

    @Test
    void blueprintNodeIncludesCatalogMirrorsWithoutRegistry() {
        PlatformObject node = new PlatformObject(
                "pump-type",
                "root.platform.instance-types.pump-type",
                ObjectType.BLUEPRINT,
                "Pump type",
                "",
                null
        );

        assertThat(CatalogSchemaVariables.names(node, null))
                .containsExactlyInAnyOrder("blueprintType", "suitabilityExpression");
    }

    @Test
    void namesFollowTheObjectType() {
        DataSchema schema = DataSchema.builder("layout").field("value", FieldType.STRING).build();
        BlueprintRegistry registry = new BlueprintRegistry();
        registry.register(new BlueprintDefinition(
                "dash-id",
                "dashboard-v1",
                "",
                BlueprintType.MIXIN,
                ObjectType.DASHBOARD,
                "",
                List.of(BlueprintVariableDefinition.of("layout", "", "info", schema, true, true, null)),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
        ));
        registry.register(new BlueprintDefinition(
                "program-id",
                "process-program-v1",
                "",
                BlueprintType.MIXIN,
                ObjectType.PROCESS_PROGRAM,
                "",
                List.of(BlueprintVariableDefinition.of("programId", "", "info", schema, true, true, null)),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Instant.EPOCH,
                Instant.EPOCH
        ));

        PlatformObject dashboard = new PlatformObject(
                "main", "root.platform.dashboards.main", ObjectType.DASHBOARD, "Main", "", null);
        PlatformObject program = new PlatformObject(
                "cycle", "root.platform.process-programs.cycle", ObjectType.PROCESS_PROGRAM, "Cycle", "", null);
        PlatformObject device = new PlatformObject(
                "pump", "root.devices.pump", ObjectType.DEVICE, "Pump", "", null);

        assertThat(CatalogSchemaVariables.names(dashboard, registry)).containsExactly("layout");
        assertThat(CatalogSchemaVariables.names(program, registry)).containsExactly("programId");
        assertThat(CatalogSchemaVariables.names(device, registry)).isEmpty();
    }
}
