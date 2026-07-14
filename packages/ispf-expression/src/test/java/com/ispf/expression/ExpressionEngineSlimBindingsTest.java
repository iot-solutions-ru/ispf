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

class ExpressionEngineSlimBindingsTest {

    private static final DataSchema NUM = DataSchema.builder("n").field("value", FieldType.DOUBLE).build();

    @Test
    void constantTrueDoesNotNeedSelf() {
        ExpressionEngine engine = new ExpressionEngine();
        ExpressionEngine.CompiledExpression compiled = engine.compile("true");
        assertFalse(compiled.bindingNeeds().self());
        assertEquals(true, compiled.evaluate(fatObject(null)));
    }

    @Test
    void selfReferenceNeedsSelf() {
        ExpressionEngine engine = new ExpressionEngine();
        ExpressionEngine.CompiledExpression compiled = engine.compile("self.sineWave.value > 0.0");
        assertTrue(compiled.bindingNeeds().self());
        assertEquals(true, compiled.evaluate(fatObject(1.5)));
    }

    @Test
    void contextReferenceNeedsContextNotSelf() {
        ExpressionEngine engine = new ExpressionEngine();
        ExpressionEngine.CompiledExpression compiled = engine.compile("context.flag == true");
        assertFalse(compiled.bindingNeeds().self());
        assertTrue(compiled.bindingNeeds().context());
        assertEquals(true, engine.evaluate("context.flag == true", fatObject(null), Map.of("flag", true)));
    }

    private static PlatformObject fatObject(Double sineWave) {
        PlatformObject obj = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.dev",
                ObjectType.DEVICE,
                "dev",
                "",
                null
        );
        for (int i = 0; i < 50; i++) {
            obj.addVariable(new Variable(
                    "v" + i,
                    NUM,
                    true,
                    false,
                    DataRecord.single(NUM, Map.of("value", (double) i))
            ));
        }
        obj.addVariable(new Variable(
                "sineWave",
                NUM,
                true,
                false,
                sineWave == null ? null : DataRecord.single(NUM, Map.of("value", sineWave))
        ));
        return obj;
    }
}
