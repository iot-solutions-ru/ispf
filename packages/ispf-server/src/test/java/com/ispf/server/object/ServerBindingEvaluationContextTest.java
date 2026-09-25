package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.ref.PlatformRef;
import com.ispf.server.event.EventService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.query.ObjectQueryService;
import com.ispf.server.ref.PlatformRefExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerBindingEvaluationContextTest {

    private static final String PATH = "root.platform.devices.pump";
    private static final DataRecord INPUT = DataRecord.empty(DataSchema.builder("voidInput").build());

    @Mock
    ObjectManager objectManager;
    @Mock
    ObjectProvider<FunctionService> functionServiceProvider;
    @Mock
    FunctionService functionService;
    @Mock
    EventService eventService;
    @Mock
    PlatformRefExecutor platformRefExecutor;
    @Mock
    ObjectQueryService objectQueryService;
    @Mock
    PlatformObject ruleObject;

    ServerBindingEvaluationContext context;

    @BeforeEach
    void setUp() {
        context = new ServerBindingEvaluationContext(
                objectManager,
                functionServiceProvider,
                eventService,
                platformRefExecutor,
                objectQueryService,
                new ObjectMapper()
        );
    }

    @Test
    void functionFailureNamesTheCallAndKeepsTheCause() {
        when(functionServiceProvider.getObject()).thenReturn(functionService);
        IllegalStateException boom = new IllegalStateException("Expression returned empty: no_such_name");
        when(functionService.invoke(PATH, "boom", INPUT)).thenThrow(boom);

        assertThatThrownBy(() -> context.invokeFunction(PATH, "boom", INPUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Function call failed: " + PATH + ".boom: Expression returned empty: no_such_name")
                .cause()
                .isSameAs(boom);
    }

    @Test
    void nestedFunctionCallFailsWithoutClearingTheOuterGuard() {
        when(functionServiceProvider.getObject()).thenReturn(functionService);
        when(functionService.invoke(PATH, "outer", INPUT)).thenAnswer(invocation -> {
            context.invokeFunction(PATH, "inner", INPUT);
            return INPUT;
        });

        assertThatThrownBy(() -> context.invokeFunction(PATH, "outer", INPUT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Function call failed: " + PATH + ".outer: Nested function call is not allowed: "
                        + PATH + ".inner");

        when(functionService.invoke(PATH, "later", INPUT)).thenReturn(INPUT);
        assertThat(context.invokeFunction(PATH, "later", INPUT)).contains(INPUT);
    }

    @Test
    void queryFailureNamesTheRuleObject() {
        IllegalArgumentException bad = new IllegalArgumentException("bad spec");
        when(objectQueryService.executeAggregate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("count"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(PATH)
        )).thenThrow(bad);
        String spec = "{\"from\":{\"sourcePathPattern\":\"root.platform.devices.*\"}}";

        assertThatThrownBy(() -> context.queryScalar(spec, PATH, "count", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("queryScalar failed: " + PATH + ": bad spec")
                .cause()
                .isSameAs(bad);
    }

    @Test
    void queryRowsFailureKeepsTheCause() {
        IllegalStateException down = new IllegalStateException("query store down");
        when(objectQueryService.execute(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(PATH)
        )).thenThrow(down);
        String spec = "{\"from\":{\"sourcePathPattern\":\"root.platform.devices.*\"}}";

        assertThatThrownBy(() -> context.queryRows(spec, PATH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("queryRows failed: " + PATH + ": query store down")
                .cause()
                .isSameAs(down);
    }

    @Test
    void brokenQuerySpecRefFailsInsteadOfEmpty() {
        when(platformRefExecutor.readLocal(org.mockito.ArgumentMatchers.eq(ruleObject), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("unknown variable"));

        assertThatThrownBy(() -> context.resolveObjectQuerySpec("@/missing", ruleObject))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Object query spec failed")
                .hasMessageContaining("@/missing")
                .cause()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankQuerySpecStaysEmpty() {
        assertThat(context.resolveObjectQuerySpec("  ", ruleObject)).isEmpty();
    }

    @Test
    void quotedObjectQueryJsonIsNotParsedAsVariableRef() {
        String json = "{\"kind\":\"OBJECT_QUERY\",\"from\":{\"sourcePathPattern\":\"root.platform.devices.*\"},"
                + "\"select\":[{\"path\":\"seriesRows\",\"as\":\"rows\"}]}";
        String quoted = "'" + json + "'";

        assertThat(context.resolveObjectQuerySpec(quoted, ruleObject)).contains(json);
    }

    @Test
    void quotedBlankSpecStaysEmpty() {
        assertThat(context.resolveObjectQuerySpec("''", ruleObject)).isEmpty();
    }

    @Test
    void successfulFireIsTrueEvenWhenThePayloadIsEmpty() {
        when(platformRefExecutor.fire(
                org.mockito.ArgumentMatchers.any(PlatformRef.class),
                org.mockito.ArgumentMatchers.eq(PATH),
                org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(Optional.empty());

        assertThat(context.fireEvent(PATH, "overload")).contains(Boolean.TRUE);
        verify(platformRefExecutor).fire(
                org.mockito.ArgumentMatchers.any(PlatformRef.class),
                org.mockito.ArgumentMatchers.eq(PATH),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void fireFailurePropagates() {
        doThrow(new IllegalStateException("Event fire failed: " + PATH + ".overload: missing"))
                .when(platformRefExecutor)
                .fire(
                        org.mockito.ArgumentMatchers.any(PlatformRef.class),
                        org.mockito.ArgumentMatchers.eq(PATH),
                        org.mockito.ArgumentMatchers.isNull()
                );

        assertThatThrownBy(() -> context.fireEvent(PATH, "overload"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Event fire failed")
                .hasMessageContaining("overload");
    }
}
