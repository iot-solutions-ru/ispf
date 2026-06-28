package com.ispf.driver.dlms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DlmsPointTest {

    @Test
    void parsesLogicalDeviceObisAndDefaults() throws Exception {
        DlmsPoint point = DlmsPoint.parse("1:1.0.1.8.0.255");
        assertEquals(1, point.logicalDevice());
        assertEquals("1.0.1.8.0.255", point.obis());
        assertEquals(gurux.dlms.enums.ObjectType.REGISTER, point.objectType());
        assertEquals(2, point.attributeIndex());
    }

    @Test
    void parsesExplicitObjectTypeAndAttribute() throws Exception {
        DlmsPoint point = DlmsPoint.parse("1:0.0.42.0.0.255:DATA:2");
        assertEquals(gurux.dlms.enums.ObjectType.DATA, point.objectType());
        assertEquals(2, point.attributeIndex());
    }

    @Test
    void rejectsMissingObis() {
        assertThrows(Exception.class, () -> DlmsPoint.parse("1:"));
    }
}
