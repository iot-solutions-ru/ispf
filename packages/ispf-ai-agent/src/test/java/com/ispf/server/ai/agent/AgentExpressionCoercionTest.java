package com.ispf.server.ai.agent;

import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentExpressionCoercionTest {

    @Test
    void coercesWordAndOrInCelConditions() {
        assertEquals(
                "self.sumIntFloat.value >= 50 && self.sumIntFloat.value <= 100",
                AgentExpressionCoercion.coerceCelCondition(
                        "self.sumIntFloat.value >= 50 and self.sumIntFloat.value <= 100"
                )
        );
        assertEquals(
                "a > 1 || b < 2",
                AgentExpressionCoercion.coerceCelCondition("a > 1 or b < 2")
        );
    }

    @Test
    void rewritesObviousTautologyUsingWatchVariable() {
        assertEquals(
                "self.sineWave[\"value\"] > 0",
                AgentExpressionCoercion.coerceCelCondition("true", "sineWave")
        );
        assertEquals(
                "self.clusterError[\"value\"] == true",
                AgentExpressionCoercion.coerceCelCondition("1 == 1", "clusterError")
        );
        assertEquals(
                "self.temperature[\"value\"] > 0",
                AgentExpressionCoercion.coerceCelCondition("true || false", "temperature")
        );
        // Without watch, leave as-is (formal verify will reject).
        assertEquals("true", AgentExpressionCoercion.coerceCelCondition("true"));
    }

    @Test
    void coercesInventedTableSumToSumRecordField() {
        PlatformObject host = hostWithTable();

        assertEquals(
                "sumRecordField(table, int)",
                AgentExpressionCoercion.coerceBindingExpression(
                        "sumRecordField(self.telemetryTable.rows, value)",
                        host
                )
        );
        assertEquals(
                "sumRecordField(table, int)",
                AgentExpressionCoercion.coerceBindingExpression(
                        "self.telemetryTable.rows.map(r | r.value).sum()",
                        host
                )
        );
        assertEquals(
                "sumRecordField(table, int)",
                AgentExpressionCoercion.coerceBindingExpression(
                        "self.telemetryTable.rows.reduce(0, (acc, r) | acc + r.value)",
                        host
                )
        );
    }

    @Test
    void leavesValidSumRecordFieldUntouched() {
        PlatformObject host = hostWithTable();
        String expr = "sumRecordField(table, int)";
        assertEquals(expr, AgentExpressionCoercion.coerceBindingExpression(expr, host));
        assertEquals(
                "self.intValue.value + self.floatValue.value",
                AgentExpressionCoercion.coerceBindingExpression("self.intValue.value + self.floatValue.value", host)
        );
    }

    private static PlatformObject hostWithTable() {
        PlatformObject host = new PlatformObject(
                "vd", "root.platform.devices.vd", ObjectType.DEVICE, "vd", "", "virtual-lab-v1"
        );
        DataSchema numeric = DataSchema.builder("v").field("value", FieldType.DOUBLE).build();
        // Presence of the variable name is enough for coercion; schema need not be RECORD_LIST here.
        host.addVariable(new Variable("table", numeric, false, true, null, false, null));
        return host;
    }
}
