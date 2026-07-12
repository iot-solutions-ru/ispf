package com.ispf.server.ref;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.ref.PlatformRef;
import com.ispf.core.ref.PlatformRefParser;
import com.ispf.server.event.EventService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformRefExecutorTest {

    @Mock
    ObjectManager objectManager;
    @Mock
    FunctionService functionService;
    @Mock
    EventService eventService;
    @Mock
    com.ispf.core.object.ObjectTree objectTree;

    @Test
    void readResolvesVariableField() {
        PlatformObject remote = deviceWithTemperature("root.platform.devices.remote", 42.0);
        when(objectManager.tree()).thenReturn(objectTree);
        when(objectTree.findByPath("root.platform.devices.remote")).thenReturn(Optional.of(remote));

        PlatformRefExecutor executor = new PlatformRefExecutor(objectManager, functionService, eventService);
        Optional<Object> value = executor.read(
                PlatformRefParser.parse("root.platform.devices.remote/temperature"),
                "root.platform.devices.local"
        );

        assertThat(value).contains(42.0);
    }

    @Test
    void firePublishesEvent() {
        PlatformRefExecutor executor = new PlatformRefExecutor(objectManager, functionService, eventService);
        PlatformRef evt = PlatformRefParser.parse("root.platform.devices.pump/evt/overload");
        executor.fire(evt, "root.platform.devices.local", null);
        verify(eventService).fire(
                eq("root.platform.devices.pump"),
                eq("overload"),
                nullable(DataRecord.class)
        );
    }

    private static PlatformObject deviceWithTemperature(String path, double temp) {
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
        PlatformObject object = new PlatformObject(
                UUID.randomUUID().toString(),
                path,
                ObjectType.DEVICE,
                "device",
                "",
                null
        );
        object.addVariable(new Variable(
                "temperature",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", temp))
        ));
        return object;
    }
}
