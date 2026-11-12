package com.ispf.server.driver;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldDefinition;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NatsDriverWriteForwarderTest {

    @Test
    void requestPayloadRoundTripsThePointValue() {
        NatsDriverWriteForwarder forwarder = new NatsDriverWriteForwarder(null, null, null, new ObjectMapper());
        DataRecord value = DataRecord.single(
                DataSchema.builder("v").field("value", FieldType.BOOLEAN).build(),
                Map.of("value", true)
        );

        NatsDriverWriteForwarder.ParsedRequest parsed = forwarder.decodeRequest(
                forwarder.encodeRequest("root.platform.devices.pump-01", "cmdOpen", value)
        );

        assertEquals("root.platform.devices.pump-01", parsed.devicePath());
        assertEquals("cmdOpen", parsed.pointId());
        assertEquals(true, parsed.value().firstRow().get("value"));
    }

    @Test
    void requestPayloadKeepsIntegerAndDoubleTypes() {
        NatsDriverWriteForwarder forwarder = new NatsDriverWriteForwarder(null, null, null, new ObjectMapper());
        DataRecord value = DataRecord.single(
                DataSchema.builder("v")
                        .field("count", FieldType.INTEGER)
                        .field("level", FieldType.DOUBLE)
                        .build(),
                Map.of("count", 12, "level", 1.5d)
        );

        NatsDriverWriteForwarder.ParsedRequest parsed = forwarder.decodeRequest(
                forwarder.encodeRequest("root.platform.devices.pump-01", "setpoint", value)
        );

        assertEquals(12, parsed.value().firstRow().get("count"));
        assertEquals(1.5d, parsed.value().firstRow().get("level"));
    }

    @Test
    void nestedRecordsAreNotForwarded() {
        NatsDriverWriteForwarder forwarder = new NatsDriverWriteForwarder(null, null, null, new ObjectMapper());
        DataRecord nested = DataRecord.single(
                DataSchema.builder("inner").field("value", FieldType.STRING).build(),
                Map.of("value", "x")
        );
        DataRecord value = DataRecord.single(
                DataSchema.builder("v").field(FieldDefinition.create(
                        "payload",
                        FieldType.RECORD,
                        null,
                        true,
                        nested.schema()
                )).build(),
                Map.of("payload", nested)
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                forwarder.encodeRequest("root.platform.devices.pump-01", "setpoint", value)
        );
        assertEquals(true, ex.getMessage().contains("RECORD"));
    }
}
