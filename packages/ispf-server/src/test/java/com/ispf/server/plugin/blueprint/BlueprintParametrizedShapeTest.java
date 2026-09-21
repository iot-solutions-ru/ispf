package com.ispf.server.plugin.blueprint;

import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.blueprint.BlueprintAlertTemplate;
import com.ispf.plugin.blueprint.BlueprintApplyResult;
import com.ispf.plugin.blueprint.BlueprintAttachment;
import com.ispf.plugin.blueprint.BlueprintDefinition;
import com.ispf.plugin.blueprint.BlueprintEngine;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.plugin.blueprint.BlueprintSqlBindingTemplate;
import com.ispf.plugin.blueprint.BlueprintType;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlueprintParametrizedShapeTest {

    @Mock
    private BlueprintEngine blueprintEngine;
    @Mock
    private BlueprintRegistry blueprintRegistry;
    @Mock
    private BlueprintBindingRulesMerger bindingRulesMerger;
    @Mock
    private ObjectManager objectManager;
    @Mock
    private SqlBindingObjectService sqlBindingObjectService;
    @Mock
    private AlertRuleService alertRuleService;

    private final BlueprintParameterResolver parameterResolver = new BlueprintParameterResolver();
    private BlueprintApplicationService applicationService;
    private ObjectTree tree;

    @BeforeEach
    void setUp() {
        applicationService = new BlueprintApplicationService(
                blueprintEngine,
                blueprintRegistry,
                bindingRulesMerger,
                objectManager,
                parameterResolver,
                sqlBindingObjectService,
                alertRuleService
        );
        tree = new ObjectTree();
    }

    @Test
    void parameterResolverSeedsSelfKeysAndSupportsDotNotation() {
        PlatformObject self = new PlatformObject(
                "1",
                "root.platform.devices.tanks.rvs-1",
                ObjectType.DEVICE,
                "РВС-1",
                "",
                null
        );
        Map<String, String> params = parameterResolver.parametersFor(self, Map.of("code", "RVS-1"));

        assertThat(params)
                .containsEntry("self.path", "root.platform.devices.tanks.rvs-1")
                .containsEntry("self.name", "rvs-1")
                .containsEntry("self.displayName", "РВС-1")
                .containsEntry("code", "RVS-1");
        assertThat(parameterResolver.resolve(
                "SELECT level_cm FROM oc_measurement WHERE tank_code='${code}' /* ${self.path} */",
                params
        )).isEqualTo(
                "SELECT level_cm FROM oc_measurement WHERE tank_code='RVS-1' /* root.platform.devices.tanks.rvs-1 */"
        );
    }

    @Test
    void applyMaterializesSqlBindingsAndAlertRulesWithResolvedParameters() {
        when(objectManager.tree()).thenReturn(tree);
        String objectPath = "root.platform.devices.tanks.rvs-1";
        PlatformObject target = new PlatformObject("1", objectPath, ObjectType.DEVICE, "РВС-1", "", null);
        tree.register(target);

        BlueprintDefinition model = tankBlueprint();
        when(blueprintEngine.applyBlueprint(model.id(), objectPath)).thenReturn(new BlueprintApplyResult(
                new BlueprintAttachment("att-1", model.id(), model.name(), BlueprintType.INSTANCE, objectPath, Instant.now()),
                List.of()
        ));
        when(objectManager.require(objectPath)).thenReturn(target);

        applicationService.applyBlueprintWithRules(model, objectPath, Map.of("code", "RVS-1"));

        ArgumentCaptor<SqlBindingObjectService.BindingDefinition> bindingCaptor =
                ArgumentCaptor.forClass(SqlBindingObjectService.BindingDefinition.class);
        verify(sqlBindingObjectService).upsert(bindingCaptor.capture());
        SqlBindingObjectService.BindingDefinition binding = bindingCaptor.getValue();
        assertThat(binding.targetObjectPath()).isEqualTo(objectPath);
        assertThat(binding.variable()).isEqualTo("levelCm");
        assertThat(binding.query()).contains("tank_code='RVS-1'");
        assertThat(binding.dataSourcePath()).isEqualTo("root.platform.data-sources.oil-control");
        assertThat(binding.refreshIntervalMs()).isEqualTo(5000L);

        ArgumentCaptor<AlertRuleService.CreateAlertRuleRequest> alertCaptor =
                ArgumentCaptor.forClass(AlertRuleService.CreateAlertRuleRequest.class);
        verify(alertRuleService).create(alertCaptor.capture());
        verify(alertRuleService, never()).update(any(), any());
        AlertRuleService.CreateAlertRuleRequest alert = alertCaptor.getValue();
        assertThat(alert.objectPath()).isEqualTo(objectPath);
        assertThat(alert.watchVariable()).isEqualTo("levelCm");
        assertThat(alert.conditionExpr()).contains("RVS-1");
        assertThat(alert.eventName()).isEqualTo("levelHigh");
        assertThat(alert.enabled()).isTrue();

        verify(bindingRulesMerger).mergeBlueprintRules(eq(objectPath), eq(model), any());
        verify(objectManager).persistNodeTree(objectPath);
    }

    private static BlueprintDefinition tankBlueprint() {
        Instant now = Instant.now();
        return new BlueprintDefinition(
                "bp-tank",
                "oil-control-tank-v1",
                "tank",
                BlueprintType.INSTANCE,
                ObjectType.DEVICE,
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new BlueprintSqlBindingTemplate(
                        "levelCm",
                        "SELECT COALESCE((SELECT level_cm FROM oc_measurement WHERE tank_code='${code}' ORDER BY measured_at DESC LIMIT 1), 0) AS value",
                        "root.platform.data-sources.oil-control",
                        5000L,
                        "value"
                )),
                List.of(new BlueprintAlertTemplate(
                        "tank-${code}-high",
                        "levelCm",
                        "self.levelCm.value > 1000 /* ${code} */",
                        "levelHigh",
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )),
                Map.of(),
                now,
                now
        );
    }
}
