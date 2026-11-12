package com.ispf.driver.iphost;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.driver.DriverErrorKind;
import com.ispf.driver.DriverException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WritePointUnsupportedRuntimeTest {

    @Test
    void writePointIsUnsupported() {
        IpHostDeviceDriver driver = new IpHostDeviceDriver();
        DriverException error = assertThrows(DriverException.class, () -> driver.writePoint(
                "point",
                DataRecord.single(
                        DataSchema.builder("value").field("value", FieldType.STRING).build(),
                        Map.of("value", "x"))));
        assertEquals(DriverErrorKind.UNSUPPORTED, error.kind());
    }
}
