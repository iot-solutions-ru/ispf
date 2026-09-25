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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class BindingExpressionErrorTest {

    private static final String PATH = "root.platform.devices.binding-expression-error";
    private static final DataSchema SCHEMA = DataSchema.builder("flag")
            .field("value", FieldType.DOUBLE)
            .build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private BindingRuleEngine bindingRuleEngine;

    @AfterEach
    void cleanup() {
        if (objectManager.tree().findByPath(PATH).isPresent()) {
            objectManager.delete(PATH);
        }
    }

    @Test
    void uncomputableExpressionFailsRecalcInsteadOfEmptySuccess() {
        ensureObject();
        bindingRulesService.saveRules(PATH, List.of(rule("no_such_name")));

        assertThatThrownBy(() -> bindingRuleEngine.runRulesForObject(PATH))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no_such_name")
                .hasMessageContaining("Binding expression failed");
        assertThat(currentValue()).isEqualTo(0.0);
    }

    private BindingRule rule(String expression) {
        return new BindingRule(
                "set-flag",
                "set-flag",
                true,
                1,
                new BindingActivators(true, List.of(), null, 0),
                "",
                expression,
                new BindingTarget("flag", "value")
        );
    }

    private Object currentValue() {
        return objectManager.require(PATH).getVariable("flag").orElseThrow()
                .value().orElseThrow().firstRow().get("value");
    }

    private void ensureObject() {
        if (objectManager.tree().findByPath(PATH).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "binding-expression-error",
                    ObjectType.DEVICE,
                    "binding-expression-error",
                    "",
                    null
            );
        }
        if (objectManager.require(PATH).getVariable("flag").isEmpty()) {
            objectManager.createVariable(
                    PATH,
                    "flag",
                    SCHEMA,
                    true,
                    true,
                    DataRecord.single(SCHEMA, Map.of("value", 0.0)),
                    false,
                    null
            );
        }
    }
}
