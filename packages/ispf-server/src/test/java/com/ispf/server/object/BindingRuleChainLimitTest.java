package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class BindingRuleChainLimitTest {

    private static final String PATH = "root.platform.devices.binding-chain-limit";
    private static final DataSchema SCHEMA = DataSchema.builder("counter")
            .field("value", FieldType.DOUBLE)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private BindingRuleEngine bindingRuleEngine;

    @AfterEach
    void cleanup() throws Exception {
        depth().set(0);
        if (objectManager.tree().findByPath(PATH).isPresent()) {
            objectManager.delete(PATH);
        }
    }

    @Test
    void passLimitFailsInsteadOfReturningSuccess() {
        if (objectManager.tree().findByPath(PATH).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "binding-chain-limit",
                    ObjectType.DEVICE,
                    "binding-chain-limit",
                    "",
                    null
            );
        }
        if (objectManager.require(PATH).getVariable("counter").isEmpty()) {
            objectManager.createVariable(
                    PATH,
                    "counter",
                    SCHEMA,
                    true,
                    true,
                    DataRecord.single(SCHEMA, Map.of("value", 0.0)),
                    false,
                    null
            );
        }
        bindingRulesService.saveRules(PATH, List.of(new BindingRule(
                "increment",
                "increment",
                true,
                1,
                new BindingActivators(true, List.of(), null, 0),
                "",
                "self.counter.value + 1.0",
                new BindingTarget("counter", "value")
        )));

        assertThatThrownBy(() -> bindingRuleEngine.runRulesForObject(PATH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PATH)
                .hasMessageContaining("truncated")
                .hasMessageContaining("pass limit");
    }

    @Test
    void depthLimitFailsInsteadOfReturning() throws Exception {
        depth().set(16);
        assertThatThrownBy(() -> bindingRuleEngine.onVariableChanged(PATH, PATH, "counter"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PATH)
                .hasMessageContaining("truncated")
                .hasMessageContaining("depth limit");
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<Integer> depth() throws Exception {
        Field field = BindingRuleEngine.class.getDeclaredField("ACTIVATION_DEPTH");
        field.setAccessible(true);
        return (ThreadLocal<Integer>) field.get(null);
    }
}
