package com.ispf.server.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ServerBindingEvaluationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpressionEvaluationServiceBreakpointTest {

    private static final String PATH = "root.dev.expr-debug";
    private static final DataSchema NUM = DataSchema.builder("num").field("value", FieldType.DOUBLE).build();

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ServerBindingEvaluationContext bindingContext;

    private ExpressionEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new ExpressionEvaluationService(objectManager, bindingContext);
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                PATH,
                ObjectType.DEVICE,
                "expr-debug",
                "",
                null
        );
        node.addVariable(new Variable(
                "temperature",
                NUM,
                true,
                false,
                DataRecord.single(NUM, Map.of("value", 21.5))
        ));
        when(objectManager.require(PATH)).thenReturn(node);
    }

    @Test
    void pausesBeforeEvaluateWhenBreakpointSet() {
        ExpressionEvaluationService.EvaluateResult result = service.evaluate(
                PATH,
                "self.temperature.value",
                null,
                List.of("evaluate"),
                null
        );

        assertThat(result.paused()).isTrue();
        assertThat(result.pausedAt()).isEqualTo("evaluate");
        assertThat(result.result()).isNull();
        assertThat(result.steps()).anyMatch(step ->
                "evaluate".equals(step.phase()) && "paused".equals(step.status()));
        assertThat(result.steps()).noneMatch(step ->
                "evaluate".equals(step.phase()) && "ok".equals(step.status()));
    }

    @Test
    void resumeFromBreakpointCompletesEvaluation() {
        ExpressionEvaluationService.EvaluateResult paused = service.evaluate(
                PATH,
                "self.temperature.value",
                null,
                List.of("evaluate"),
                null
        );
        assertThat(paused.paused()).isTrue();

        ExpressionEvaluationService.EvaluateResult resumed = service.evaluate(
                PATH,
                "self.temperature.value",
                null,
                List.of("evaluate"),
                "evaluate"
        );

        assertThat(resumed.paused()).isFalse();
        assertThat(resumed.valid()).isTrue();
        assertThat(resumed.result()).isEqualTo(21.5);
        assertThat(resumed.steps()).anyMatch(step ->
                "evaluate".equals(step.phase()) && "ok".equals(step.status()));
    }

    @Test
    void pausesOnCelBindingsPhase() {
        ExpressionEvaluationService.EvaluateResult result = service.evaluate(
                PATH,
                "self.temperature.value * 2.0",
                null,
                List.of("cel-bindings"),
                null
        );

        assertThat(result.paused()).isTrue();
        assertThat(result.pausedAt()).isEqualTo("cel-bindings");
        assertThat(result.steps()).anyMatch(step -> "compile-cel".equals(step.phase()));
        assertThat(result.steps()).noneMatch(step ->
                "cel-bindings".equals(step.phase()) && "ok".equals(step.status()));
    }
}
