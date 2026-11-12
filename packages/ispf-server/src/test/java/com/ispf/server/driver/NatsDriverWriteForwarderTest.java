package com.ispf.server.driver;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
