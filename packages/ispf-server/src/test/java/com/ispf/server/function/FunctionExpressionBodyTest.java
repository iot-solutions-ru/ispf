package com.ispf.server.function;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionExpressionBodyTest {

    @Test
    void parsePlainString() {
        FunctionExpressionBody body = FunctionExpressionBody.parse("input.value * 2");
        assertThat(body.expression()).isEqualTo("input.value * 2");
        assertThat(body.hasFormulaRef()).isFalse();
    }

    @Test
    void parseJsonWithFormulaMetadata() {
        FunctionExpressionBody body = FunctionExpressionBody.parse("""
                {
                  "expression": "rateOfChange({{levelPath}}, 1h)",
                  "formulaRef": "tank-fill-rate",
                  "formulaParams": {"levelPath": "root.dev.tank.level"},
                  "formulaScope": "site"
                }
                """);
        assertThat(body.expression()).contains("rateOfChange");
        assertThat(body.formulaRef()).isEqualTo("tank-fill-rate");
        assertThat(body.formulaParams()).containsEntry("levelPath", "root.dev.tank.level");
        assertThat(body.formulaScope()).isEqualTo("site");
    }

    @Test
    void serializePlainExpression() {
        FunctionExpressionBody body = new FunctionExpressionBody("scale(x, 0, 1, 0, 100)", null, Map.of(), null, null);
        assertThat(body.serialize()).isEqualTo("scale(x, 0, 1, 0, 100)");
    }

    @Test
    void serializeWithFormulaRef() {
        FunctionExpressionBody body = new FunctionExpressionBody(
                "rateOfChange({{levelPath}}, 1h)",
                "tank-fill-rate",
                Map.of("levelPath", "root.dev.tank.level"),
                "site",
                null
        );
        String json = body.serialize();
        FunctionExpressionBody roundTrip = FunctionExpressionBody.parse(json);
        assertThat(roundTrip.formulaRef()).isEqualTo("tank-fill-rate");
        assertThat(roundTrip.formulaParams()).containsEntry("levelPath", "root.dev.tank.level");
    }

    @Test
    void rejectsBlankBody() {
        assertThatThrownBy(() -> FunctionExpressionBody.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
