package com.ispf.server.platform.time;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectTree;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * F-06: pure unit coverage (was {@code @SpringBootTest} with disposable device create/delete).
 */
@ExtendWith(MockitoExtension.class)
class PlatformTimeZoneResolverTest {

    private static final String DEVICE_PATH = "root.platform.devices.tz-resolver-it";

    @Mock
    private ObjectManager objectManager;

    private ObjectTree tree;
    private PlatformTimeZoneResolver resolver;

    @BeforeEach
    void setUp() {
        tree = new ObjectTree();
        when(objectManager.tree()).thenReturn(tree);
        when(objectManager.require(anyString())).thenAnswer(inv -> tree.require(inv.getArgument(0)));
        resolver = new PlatformTimeZoneResolver(objectManager);
    }

    @Test
    void defaultsToUtcWhenUnset() {
        assertThat(resolver.resolve("root.platform.security")).isEqualTo("UTC");
    }

    @Test
    void resolvesDeviceTimeZone() {
        PlatformObject device = new PlatformObject(
                "tz-resolver-it",
                DEVICE_PATH,
                ObjectType.DEVICE,
                "tz-resolver-it",
                "",
                null
        );
        DataSchema schema = DataSchema.builder("timeZone").field("value", FieldType.STRING).build();
        device.addVariable(new Variable(
                "timeZone",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", "Asia/Yekaterinburg"))
        ));
        tree.register(device);

        assertThat(resolver.resolve(DEVICE_PATH)).isEqualTo("Asia/Yekaterinburg");
    }
}
