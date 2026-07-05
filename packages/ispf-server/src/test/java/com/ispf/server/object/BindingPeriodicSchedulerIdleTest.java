package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BindingPeriodicSchedulerIdleTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private BindingPeriodicScheduleRegistry registry;

    @Autowired
    private BindingPeriodicScheduler periodicScheduler;

    private String targetVariable;
    private String ruleId;

    @AfterEach
    void cleanup() {
        registry.clearAll();
        periodicScheduler.reschedule();
        if (ruleId != null) {
            bindingRulesService.deleteRule(DEVICE, ruleId);
            ruleId = null;
        }
        if (targetVariable != null) {
            objectManager.deleteVariable(DEVICE, targetVariable);
            targetVariable = null;
        }
    }

    @Test
    void rescheduleDoesNotScheduleWakeWhenRegistryEmpty() {
        registry.clearAll();

        periodicScheduler.reschedule();

        assertThat(registry.countEnabled()).isZero();
        assertThat(periodicScheduler.isWakeScheduled()).isFalse();
    }

    @Test
    void periodicRuleSchedulesWakeAndRunsOnTick() {
        targetVariable = "bindPeriodicIdle" + System.nanoTime();
        ruleId = "rule-" + targetVariable;
        ensureDoubleVariable(targetVariable, 0.0);

        bindingRulesService.upsertRule(DEVICE, new BindingRule(
                ruleId,
                targetVariable,
                true,
                0,
                new BindingActivators(false, List.of(), null, 100),
                "",
                "9.5",
                new BindingTarget(targetVariable, "value")
        ));

        assertThat(registry.countEnabled()).isEqualTo(1);
        assertThat(periodicScheduler.isWakeScheduled()).isTrue();

        periodicScheduler.tick();

        assertThat(readDouble(targetVariable)).isEqualTo(9.5);
    }

    private void ensureDoubleVariable(String name, double initial) {
        if (objectManager.require(DEVICE).getVariable(name).isEmpty()) {
            DataSchema schema = DataSchema.builder(name).field("value", FieldType.DOUBLE).build();
            objectManager.createVariable(
                    DEVICE,
                    name,
                    schema,
                    true,
                    false,
                    DataRecord.single(schema, Map.of("value", initial)),
                    false,
                    null
            );
        }
    }

    private double readDouble(String name) {
        return objectManager.require(DEVICE).getVariable(name)
                .flatMap(v -> v.value())
                .map(record -> ((Number) record.firstRow().get("value")).doubleValue())
                .orElseThrow();
    }
}
