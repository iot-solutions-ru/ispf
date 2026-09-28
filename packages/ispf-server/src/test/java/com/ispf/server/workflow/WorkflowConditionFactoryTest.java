package com.ispf.server.workflow;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.expression.ExpressionEngine;
import com.ispf.plugin.workflow.WorkflowConditionEvaluator;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.spi.WorkflowObjectAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowConditionFactoryTest {

    private static final String PATH = "root.platform.devices.pump";

    @Mock
    private WorkflowObjectAccess objects;

    private WorkflowConditionFactory factory;

    @BeforeEach
    void setUp() {
        factory = new WorkflowConditionFactory(objects, new ExpressionEngine());
    }

    @Test
    void uncomputableConditionFailsInsteadOfActingAsFalse() {
        PlatformObject node = deviceWithFlag(0.0);
        when(objects.require(PATH)).thenReturn(node);
        WorkflowConditionEvaluator evaluator = factory.forTriggerObjectPath(PATH);

        assertThatThrownBy(() -> evaluator.evaluate("no_such_name"))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("Workflow gateway condition failed")
                .hasMessageContaining("no_such_name");
    }

    @Test
    void falseConditionRemainsFalse() throws WorkflowException {
        PlatformObject node = deviceWithFlag(0.0);
        when(objects.require(PATH)).thenReturn(node);
        WorkflowConditionEvaluator evaluator = factory.forTriggerObjectPath(PATH);

        assertThat(evaluator.evaluate("false")).isFalse();
    }

    @Test
    void blankConditionIsAlwaysTrue() throws WorkflowException {
        WorkflowConditionEvaluator evaluator = factory.forTriggerObjectPath(PATH);

        assertThat(evaluator.evaluate("")).isTrue();
        assertThat(evaluator.evaluate("  ")).isTrue();
    }

    @Test
    void missingTriggerObjectFailsNonBlankCondition() {
        WorkflowConditionEvaluator evaluator = factory.forTriggerObjectPath("");

        assertThatThrownBy(() -> evaluator.evaluate("true"))
                .isInstanceOf(WorkflowException.class)
                .hasMessageContaining("missing trigger object");
    }

    private static PlatformObject deviceWithFlag(double value) {
        DataSchema schema = DataSchema.builder("flag").field("value", FieldType.DOUBLE).build();
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                PATH,
                ObjectType.DEVICE,
                "pump",
                "",
                null
        );
        node.addVariable(new Variable(
                "flag",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", value))
        ));
        return node;
    }
}
