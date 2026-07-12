package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.FunctionDescriptor;
import com.ispf.server.platform.analytics.formula.AnalyticsFormula;
import com.ispf.server.platform.analytics.formula.AnalyticsFormulaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FunctionExpressionResolverTest {

    @Mock
    private AnalyticsFormulaService formulaService;

    @InjectMocks
    private FunctionExpressionResolver resolver;

    @Test
    void expandsFormulaRef() {
        FunctionExpressionBody body = new FunctionExpressionBody(
                "rateOfChange({{levelPath}}, 1h)",
                "tank-fill-rate",
                Map.of("levelPath", "root.dev.tank.level"),
                AnalyticsFormula.SCOPE_SITE,
                null
        );
        when(formulaService.expand(
                "tank-fill-rate",
                body.formulaParams(),
                AnalyticsFormula.SCOPE_SITE,
                null
        )).thenReturn("rateOfChange(root.dev.tank.level, 1h)");

        assertThat(resolver.resolve(body)).isEqualTo("rateOfChange(root.dev.tank.level, 1h)");
    }

    @Test
    void returnsPlainExpressionWhenNoFormulaRef() {
        FunctionExpressionBody body = new FunctionExpressionBody("input.value * 2", null, Map.of(), null, null);
        assertThat(resolver.resolve(body)).isEqualTo("input.value * 2");
    }
}
