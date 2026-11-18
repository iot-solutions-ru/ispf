package com.ispf.server.history;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.ObjectChangeEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VariableHistoryListenerTest {

    @Test
    void publicationSampleUsesTheEventValue() {
        VariableHistoryService history = mock(VariableHistoryService.class);
        VariableHistoryListener listener = new VariableHistoryListener(history);
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
        DataRecord published = DataRecord.single(schema, Map.of("value", -22.4));
        Instant observedAt = Instant.parse("2026-11-18T12:00:00Z");

        listener.handle(ObjectChangeEvent.variableUpdated(
                "root.platform.devices.room",
                "temperature",
                true,
                false,
                observedAt,
                published,
                null
        ));

        verify(history).recordFromDataRecord(
                "root.platform.devices.room",
                "temperature",
                published,
                observedAt
        );
        verify(history, never()).recordVariableUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void historyPolicyChangeDoesNotSample() {
        VariableHistoryService history = mock(VariableHistoryService.class);
        VariableHistoryListener listener = new VariableHistoryListener(history);

        listener.handle(ObjectChangeEvent.variableUpdated(
                "root.platform.devices.room",
                "temperature",
                4L,
                "admin"
        ));

        verify(history, never()).recordVariableUpdate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(history, never()).recordFromDataRecord(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void measurementWithoutRevisionStillSamples() {
        VariableHistoryService history = mock(VariableHistoryService.class);
        VariableHistoryListener listener = new VariableHistoryListener(history);

        listener.handle(ObjectChangeEvent.variableUpdated(
                "root.platform.devices.room",
                "temperature"
        ));

        verify(history).recordVariableUpdate(
                "root.platform.devices.room",
                "temperature",
                null
        );
    }
}
