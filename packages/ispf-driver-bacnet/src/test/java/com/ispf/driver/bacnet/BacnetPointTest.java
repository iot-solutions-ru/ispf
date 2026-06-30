package com.ispf.driver.bacnet;

import com.ispf.driver.DriverException;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacnetPointTest {

    @Test
    void parsesAnalogValuePresentValueMapping() throws DriverException {
        BacnetPoint point = BacnetPoint.parse("analog-value:1:present-value");
        assertEquals(1, point.instance());
        assertEquals(PropertyIdentifier.presentValue, point.property());
    }

    @Test
    void rejectsShortMapping() {
        assertThrows(DriverException.class, () -> BacnetPoint.parse("analog-value:1"));
    }
}
