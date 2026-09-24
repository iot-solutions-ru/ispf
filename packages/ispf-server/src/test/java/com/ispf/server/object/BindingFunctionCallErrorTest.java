package com.ispf.server.object;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.core.object.ObjectType;
import com.ispf.server.function.FunctionService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class BindingFunctionCallErrorTest {

    private static final String PATH = "root.platform.devices.binding-call-error";
    private static final DataSchema OUT = DataSchema.builder("out").field("value", FieldType.DOUBLE).build();

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private BindingRuleEngine bindingRuleEngine;

    @Autowired
    private FunctionService functionService;

    @Autowired
    private ServerBindingEvaluationContext evaluationContext;

    @AfterEach
    void cleanup() throws Exception {
        invokeGuard().set(false);
        if (objectManager.tree().findByPath(PATH).isPresent()) {
            objectManager.delete(PATH);
        }
    }

    @Test
    void ruleFailsWhenCalledFunctionThrows() {
        ensureObject();
        objectManager.upsertFunction(PATH, new FunctionDescriptor(
                "boom",
                "always fails",
                DataSchema.builder("in").build(),
                OUT,
                "expression",
                "no_such_name",
                null,
                "1"
        ));
        bindingRulesService.saveRules(PATH, List.of(new BindingRule(
                "from-call",
                "from-call",
                true,
                1,
                new BindingActivators(true, List.of(), null, 0),
                "",
                "call(@/fn/boom)",
                new BindingTarget("result", "value")
        )));

        assertThatThrownBy(() -> functionService.invoke(PATH, "boom"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> bindingRuleEngine.runRulesForObject(PATH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Function call failed")
                .hasMessageContaining("boom");
        assertThat(objectManager.require(PATH).getVariable("result").orElseThrow().value())
                .isPresent()
                .get()
                .extracting(record -> record.firstRow().get("value"))
                .isEqualTo(0.0);
    }

    @Test
    void nestedInvokeWhileGuardHeldIsNotEmptySuccess() throws Exception {
        invokeGuard().set(true);
        assertThatThrownBy(() -> evaluationContext.invokeFunction(PATH, "boom", DataRecord.empty(OUT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nested function call is not allowed")
                .hasMessageContaining("boom");
    }

    private void ensureObject() {
        if (objectManager.tree().findByPath(PATH).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "binding-call-error",
                    ObjectType.DEVICE,
                    "binding-call-error",
                    "",
                    null
            );
        }
        if (objectManager.require(PATH).getVariable("result").isEmpty()) {
            objectManager.createVariable(
                    PATH,
                    "result",
                    OUT,
                    true,
                    true,
                    DataRecord.single(OUT, Map.of("value", 0.0)),
                    false,
                    null
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<Boolean> invokeGuard() throws Exception {
        Field field = ServerBindingEvaluationContext.class.getDeclaredField("INVOKE_GUARD");
        field.setAccessible(true);
        return (ThreadLocal<Boolean>) field.get(null);
    }
}
