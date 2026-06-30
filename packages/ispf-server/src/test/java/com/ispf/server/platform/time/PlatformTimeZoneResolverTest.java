package com.ispf.server.platform.time;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlatformTimeZoneResolverTest {

    @Autowired
    private PlatformTimeZoneResolver resolver;

    @Autowired
    private ObjectManager objectManager;

    @Test
    void defaultsToUtcWhenUnset() {
        assertThat(resolver.resolve("root.platform.security")).isEqualTo("UTC");
    }

    @Test
    @Transactional
    void resolvesDeviceTimeZone() {
        String path = "root.platform.devices.mini-tec-plant.gpu-01";
        PlatformObject device = objectManager.require(path);
        DataSchema schema = DataSchema.builder("timeZone").field("value", FieldType.STRING).build();
        device.addVariable(new Variable(
                "timeZone",
                schema,
                true,
                true,
                DataRecord.single(schema, java.util.Map.of("value", "Asia/Yekaterinburg"))
        ));
        assertThat(resolver.resolve(path)).isEqualTo("Asia/Yekaterinburg");
    }
}
