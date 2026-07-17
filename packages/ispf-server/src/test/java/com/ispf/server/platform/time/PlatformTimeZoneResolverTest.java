package com.ispf.server.platform.time;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlatformTimeZoneResolverTest {

    private static final String DEVICE_NAME = "tz-resolver-it";
    private static final String DEVICE_PATH = "root.platform.devices." + DEVICE_NAME;

    @Autowired
    private PlatformTimeZoneResolver resolver;

    @Autowired
    private ObjectManager objectManager;

    @AfterEach
    void cleanup() {
        objectManager.tree().findByPath(DEVICE_PATH).ifPresent(node -> objectManager.delete(DEVICE_PATH));
    }

    @Test
    void defaultsToUtcWhenUnset() {
        assertThat(resolver.resolve("root.platform.security")).isEqualTo("UTC");
    }

    @Test
    void resolvesDeviceTimeZone() {
        // mini-TEC is marketplace-only and is not fixture-seeded; create a disposable device.
        if (objectManager.tree().findByPath(DEVICE_PATH).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    DEVICE_NAME,
                    ObjectType.DEVICE,
                    DEVICE_NAME,
                    null,
                    null
            );
        }
        PlatformObject device = objectManager.require(DEVICE_PATH);
        DataSchema schema = DataSchema.builder("timeZone").field("value", FieldType.STRING).build();
        device.addVariable(new Variable(
                "timeZone",
                schema,
                true,
                true,
                DataRecord.single(schema, java.util.Map.of("value", "Asia/Yekaterinburg"))
        ));
        assertThat(resolver.resolve(DEVICE_PATH)).isEqualTo("Asia/Yekaterinburg");
    }
}
