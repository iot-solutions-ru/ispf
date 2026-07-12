package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingTargetKind;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.LabBlueprintBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class CrossObjectBindingIntegrationTest {

    private static final String HUB = "root.platform.devices.test-virt-cluster.hub";
    private static final String DEV = "root.platform.devices.test-virt-cluster.dev-01";
    private static final DataSchema SINE_WAVE = DataSchema.builder("sineWave")
            .field("value", FieldType.DOUBLE)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private BindingDependencyIndex dependencyIndex;

    @Autowired
    private BindingRuleEngine bindingRuleEngine;

    @BeforeEach
    void resetHubRules() {
        clearHubRules();
    }

    @AfterEach
    void cleanupHubRules() {
        clearHubRules();
    }

    private void clearHubRules() {
        if (objectManager.tree().findByPath(HUB).isEmpty()) {
            return;
        }
        for (BindingRule rule : bindingRulesService.listRules(HUB)) {
            bindingRulesService.deleteRule(HUB, rule.id());
        }
        dependencyIndex.rebuild(HUB);
    }

    @Test
    void crossObjectRuleUpdatesHubWhenRemoteTelemetryChanges() {
        ensureDevice(DEV);
        ensureHub(HUB);

        bindingRulesService.saveRules(HUB, List.of(
                new BindingRule(
                        "member1-sine",
                        "Member1 sine",
                        true,
                        10,
                        BindingActivators.onRemoteChange(DEV, "sineWave"),
                        "",
                        "read(\"" + DEV + "/sineWave\")",
                        new BindingTarget("member1Sine", "value")
                )
        ));
        dependencyIndex.rebuild(HUB);

        objectManager.setDriverTelemetryValue(
                DEV,
                "sineWave",
                DataRecord.single(SINE_WAVE, Map.of("value", 3.14))
        );
        bindingRuleEngine.onVariableChanged(HUB, DEV, "sineWave");

        assertThat(objectManager.require(HUB).getVariable("member1Sine"))
                .isPresent()
                .get()
                .extracting(v -> v.value().orElseThrow().firstRow().get("value"))
                .isEqualTo(3.14);
    }

    @Test
    void crossObjectRuleWritesRemoteTargetRef() {
        ensureDevice(DEV);
        ensureHub(HUB);
        ensureDeviceOutputVariable(DEV, "mirroredSine", 0.0);

        bindingRulesService.saveRules(HUB, List.of(
                new BindingRule(
                        "mirror-remote-sine",
                        "Mirror remote sine",
                        true,
                        10,
                        BindingActivators.onRemoteChange(DEV, "sineWave"),
                        "",
                        "read(\"" + DEV + "/sineWave\")",
                        new BindingTarget(
                                BindingTargetKind.VARIABLE,
                                null,
                                "value",
                                null,
                                null,
                                DEV + "/mirroredSine"
                        )
                )
        ));
        dependencyIndex.rebuild(HUB);

        objectManager.setDriverTelemetryValue(
                DEV,
                "sineWave",
                DataRecord.single(SINE_WAVE, Map.of("value", 2.71))
        );
        bindingRuleEngine.onVariableChanged(HUB, DEV, "sineWave");

        assertThat(objectManager.require(DEV).getVariable("mirroredSine"))
                .isPresent()
                .get()
                .extracting(v -> v.value().orElseThrow().firstRow().get("value"))
                .isEqualTo(2.71);
    }

    private void ensureDeviceOutputVariable(String devicePath, String name, double initial) {
        if (objectManager.require(devicePath).getVariable(name).isPresent()) {
            return;
        }
        DataSchema schema = DataSchema.builder(name).field("value", FieldType.DOUBLE).build();
        objectManager.createVariable(
                devicePath,
                name,
                schema,
                true,
                false,
                DataRecord.single(schema, Map.of("value", initial)),
                false,
                null
        );
    }

    private void ensureDevice(String path) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            String folder = "root.platform.devices.test-virt-cluster";
            if (objectManager.tree().findByPath(folder).isEmpty()) {
                objectManager.create(
                        "root.platform.devices",
                        "test-virt-cluster",
                        ObjectType.CUSTOM,
                        "test-virt-cluster",
                        "",
                        null
                );
            }
            objectManager.create(
                    folder,
                    "dev-01",
                    ObjectType.DEVICE,
                    "dev-01",
                    "",
                    LabBlueprintBootstrap.VIRTUAL_LAB_MODEL
            );
        }
    }

    private void ensureHub(String path) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            String folder = "root.platform.devices.test-virt-cluster";
            if (objectManager.tree().findByPath(folder).isEmpty()) {
                objectManager.create(
                        "root.platform.devices",
                        "test-virt-cluster",
                        ObjectType.CUSTOM,
                        "test-virt-cluster",
                        "",
                        null
                );
            }
            objectManager.create(
                    folder,
                    "hub",
                    ObjectType.CUSTOM,
                    "hub",
                    "",
                    null
            );
            objectManager.createVariable(
                    path,
                    "member1Sine",
                    DataSchema.builder("member1Sine").field("value", FieldType.DOUBLE).build(),
                    true,
                    false,
                    DataRecord.single(
                            DataSchema.builder("member1Sine").field("value", FieldType.DOUBLE).build(),
                            Map.of("value", 0.0)
                    ),
                    false,
                    null
            );
        }
    }
}
