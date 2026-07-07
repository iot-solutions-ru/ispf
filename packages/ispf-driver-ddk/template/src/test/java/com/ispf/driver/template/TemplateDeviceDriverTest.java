package com.ispf.driver.template;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.driver.DeviceDriver;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateDeviceDriverTest {

    @Test
    void readsPlaceholderValueViaLoopbackConfig() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of(
                "host", "127.0.0.1",
                "port", "9500",
                "timeoutMs", "1000"
        ));

        TemplateDeviceDriver driver = new TemplateDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("temp", "ai:room-1"));

        DataRecord record = driverObject.variables.get("temp");
        assertEquals("TEMPLATE_OK:room-1", record.firstRow().get("value"));
        assertEquals(true, record.firstRow().get("connected"));
        driver.disconnect();
    }

    @Test
    void writeRoundTripUpdatesVariable() throws Exception {
        StubDriverObject driverObject = new StubDriverObject(Map.of());
        TemplateDeviceDriver driver = new TemplateDeviceDriver();
        driver.initialize(driverObject);
        driver.connect();
        driver.readPoints(Map.of("setpoint", "ao:sp-1"));
        driver.writePoint("setpoint", DataRecord.single(
                com.ispf.core.model.DataSchema.builder("value")
                        .field("value", com.ispf.core.model.FieldType.STRING)
                        .build(),
                Map.of("value", "42")
        ));

        assertEquals("42", driverObject.variables.get("setpoint").firstRow().get("value"));
        driver.disconnect();
    }

    @Test
    void writeRequiresPriorRead() throws Exception {
        TemplateDeviceDriver driver = new TemplateDeviceDriver();
        driver.initialize(new StubDriverObject(Map.of()));
        driver.connect();

        assertThrows(DriverException.class, () -> driver.writePoint("missing", DataRecord.empty(
                com.ispf.core.model.DataSchema.builder("value").field("value", com.ispf.core.model.FieldType.STRING).build()
        )));
    }

    @Test
    void pointParserRejectsBlankMapping() {
        assertThrows(IllegalArgumentException.class, () -> TemplatePoint.parse(" "));
    }

    private static final class StubDriverObject implements DeviceDriver.DriverObject {
        private final Map<String, String> config;
        private final Map<String, DataRecord> variables = new HashMap<>();

        private StubDriverObject(Map<String, String> config) {
            this.config = config;
        }

        @Override
        public PlatformObject deviceObject() {
            return new PlatformObject(
                    "test-template",
                    "root.platform.devices.template",
                    ObjectType.DEVICE,
                    "Template",
                    "",
                    null
            );
        }

        @Override
        public void updateVariable(String name, DataRecord value) {
            variables.put(name, value);
        }

        @Override
        public Optional<DataRecord> getVariable(String name) {
            return Optional.ofNullable(variables.get(name));
        }

        @Override
        public void log(DeviceDriver.DriverLogLevel level, String message) {
        }

        @Override
        public Map<String, String> configuration() {
            return config;
        }
    }
}
