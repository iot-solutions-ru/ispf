package com.ispf.expression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeterogeneousNumericComparisonTest {

    private static final DataSchema NUM = DataSchema.builder("n").field("value", FieldType.DOUBLE).build();

    private final ExpressionEngine engine = new ExpressionEngine();

    @Test
    void doubleValueComparedToIntLiteral() {
        PlatformObject positive = objectWithValue(2.5);
        PlatformObject negative = objectWithValue(-1.5);
        PlatformObject zero = objectWithValue(0.0);

        assertTrue((Boolean) engine.evaluate("self.x.value > 0", positive));
        assertFalse((Boolean) engine.evaluate("self.x.value > 0", negative));
        assertFalse((Boolean) engine.evaluate("self.x.value > 0", zero));

        assertTrue((Boolean) engine.evaluate("self.x.value < 0", negative));
        assertFalse((Boolean) engine.evaluate("self.x.value < 0", positive));

        assertTrue((Boolean) engine.evaluate("self.x.value >= 0", positive));
        assertTrue((Boolean) engine.evaluate("self.x.value >= 0", zero));
        assertFalse((Boolean) engine.evaluate("self.x.value >= 0", negative));

        assertTrue((Boolean) engine.evaluate("self.x.value <= 0", negative));
        assertTrue((Boolean) engine.evaluate("self.x.value <= 0", zero));
        assertFalse((Boolean) engine.evaluate("self.x.value <= 0", positive));
    }

    @Test
    void intLiteralComparedToDoubleValue() {
        PlatformObject positive = objectWithValue(2.5);
        PlatformObject negative = objectWithValue(-1.5);

        assertTrue((Boolean) engine.evaluate("0 < self.x.value", positive));
        assertTrue((Boolean) engine.evaluate("0 > self.x.value", negative));
        assertFalse((Boolean) engine.evaluate("0 > self.x.value", positive));
    }

    @Test
    void doubleLiteralComparisonsStillWork() {
        PlatformObject positive = objectWithValue(1.5);

        assertTrue((Boolean) engine.evaluate("self.x.value > 0.0", positive));
        assertEquals(true, engine.evaluate("self.x.value >= 1.5", positive));
        assertFalse((Boolean) engine.evaluate("self.x.value < 0.0", positive));
    }

    @Test
    void bracketAndFieldSelectAgainstIntLiteral() {
        PlatformObject positive = objectWithValue(2.5);

        assertTrue((Boolean) engine.evaluate("self.x.value > 0", positive));
        assertTrue((Boolean) engine.evaluate("self.x[\"value\"] > 0", positive));
        assertTrue((Boolean) engine.evaluate("self.x[\"value\"] > 0.0", positive));
    }

    @Test
    void clusterErrorStyleExpression() {
        PlatformObject obj = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.hub",
                ObjectType.CUSTOM,
                "hub",
                "",
                null
        );
        addValue(obj, "member1Sine", 2.0);
        addValue(obj, "member2Sine", 8.0);
        addValue(obj, "member3Sine", 8.0);

        assertTrue((Boolean) engine.evaluate(
                "self.member1Sine.value > 0 && self.member2Sine.value > 0 && self.member3Sine.value > 0",
                obj
        ));
        assertTrue((Boolean) engine.evaluate(
                "self.member1Sine[\"value\"] > 0 && self.member2Sine[\"value\"] > 0 && self.member3Sine[\"value\"] > 0",
                obj
        ));
    }

    @Test
    void normalizeMapIndexSelectsRewritesIdentifierKeys() {
        assertEquals(
                "self.member1Sine.value > 0",
                ExpressionEngine.normalizeMapIndexSelects("self.member1Sine[\"value\"] > 0")
        );
        assertEquals("self.x.value", ExpressionEngine.normalizeMapIndexSelects("self.x[\"value\"]"));
        assertEquals("m[\"a-b\"]", ExpressionEngine.normalizeMapIndexSelects("m[\"a-b\"]"));
        assertEquals("true", ExpressionEngine.normalizeMapIndexSelects("true"));
    }

    private static PlatformObject objectWithValue(double value) {
        PlatformObject obj = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.dev",
                ObjectType.DEVICE,
                "dev",
                "",
                null
        );
        addValue(obj, "x", value);
        return obj;
    }

    private static void addValue(PlatformObject obj, String name, double value) {
        obj.addVariable(new Variable(
                name,
                NUM,
                true,
                false,
                DataRecord.single(NUM, Map.of("value", value))
        ));
    }
}
