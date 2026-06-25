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

class OgpAlertConditionTest {

    @Test
    void comparesUnprocessedPendingWithBracketAndDotSyntax() {
        DataSchema schema = DataSchema.builder("doubleValue").field("value", FieldType.DOUBLE).build();
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.ogp-mes-hub",
                ObjectType.DEVICE,
                "hub",
                "",
                null
        );
        node.addVariable(new Variable(
                "unprocessedPending",
                schema,
                true,
                false,
                DataRecord.single(schema, Map.of("value", 1.0))
        ));

        ExpressionEngine engine = new ExpressionEngine();
        assertEquals(true, engine.evaluate("self.unprocessedPending[\"value\"] > 0.0", node));
        assertEquals(true, engine.evaluate("self.unprocessedPending.value > 0.0", node));
    }

    @Test
    void comparesIntegerLikePendingCount() {
        DataSchema schema = DataSchema.builder("doubleValue").field("value", FieldType.DOUBLE).build();
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.ogp-mes-hub",
                ObjectType.DEVICE,
                "hub",
                "",
                null
        );
        node.addVariable(new Variable(
                "unprocessedPending",
                schema,
                true,
                false,
                DataRecord.single(schema, Map.of("value", 1.0))
        ));

        ExpressionEngine engine = new ExpressionEngine();
        assertEquals(true, engine.evaluate("self.unprocessedPending[\"value\"] > 0.0", node));
    }

    @Test
    void comparesSineWaveTelemetryWithBracketAndDotSyntax() {
        DataSchema schema = DataSchema.builder("sineWave").field("value", FieldType.DOUBLE).build();
        PlatformObject node = new PlatformObject(
                UUID.randomUUID().toString(),
                "root.platform.devices.lab-01",
                ObjectType.DEVICE,
                "lab",
                "",
                null
        );
        node.addVariable(new Variable(
                "sineWave",
                schema,
                true,
                false,
                DataRecord.single(schema, Map.of("value", -0.5))
        ));

        ExpressionEngine engine = new ExpressionEngine();
        assertEquals(true, engine.evaluateAlertCondition("self.sineWave[\"value\"] > -1000.0", node, "sineWave"));
        assertEquals(true, engine.evaluateAlertCondition("self.sineWave.value > -1000.0", node, "sineWave"));
    }
}
