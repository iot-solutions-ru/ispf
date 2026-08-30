package com.ispf.server.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ServerBindingEvaluationContext;
import com.ispf.server.security.acl.VariableMemberAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpressionEvaluationServiceBreakpointTest {

    private static final String PATH = "root.dev.expr-debug";
    private static final DataSchema NUM = DataSchema.builder("num").field("value", FieldType.DOUBLE).build();

    @Mock
    private ObjectManager objectManager;

    @Mock
    private ServerBindingEvaluationContext bindingContext;

    @Mock
    private VariableMemberAccessService variableMemberAccessService;

    private ExpressionEvaluationService service;
    private PlatformObject node;
    private Variable temperature;

    @BeforeEach
    void setUp() {
        service = new ExpressionEvaluationService(objectManager, bindingContext, variableMemberAccessService);
        node = new PlatformObject(
                UUID.randomUUID().toString(),
                PATH,
                ObjectType.DEVICE,
                "expr-debug",
                "",
                null
        );
        temperature = new Variable(
                "temperature",
                NUM,
                true,
                false,
                DataRecord.single(NUM, Map.of("value", 21.5))
        );
        node.addVariable(temperature);
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

    @Test
    void memberEvaluationExcludesRestrictedVariableValuesFromCelBindings() {
        node.addVariable(new Variable(
                "secretSetpoint",
                NUM,
                true,
                false,
                DataRecord.single(NUM, Map.of("value", 8675309.0)),
                false,
                null,
                List.of("engineer"),
                List.of()
        ));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator",
                "n/a",
                List.of()
        );
        when(variableMemberAccessService.filterReadable(
                eq(PATH),
                anyCollection(),
                eq(authentication)
        )).thenReturn(List.of(temperature));

        ExpressionEvaluationService.EvaluateResult result = service.evaluate(
                PATH,
                "self.temperature.value",
                null,
                List.of(),
                null,
                authentication
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.result()).isEqualTo(21.5);
        assertThat(result.steps().toString()).doesNotContain("secretSetpoint", "8675309");
    }
}
