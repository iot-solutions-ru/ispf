package com.ispf.driver.snmp;

import org.junit.jupiter.api.Test;
import org.snmp4j.mp.SnmpConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnmpVersionConfigTest {

    @Test
    void metadataIncludesV3Keys() {
        assertTrue(new SnmpDeviceDriver().metadata().configurationSchema()
                .containsKey("securityName"));
        assertTrue(new SnmpDeviceDriver().metadata().configurationSchema()
                .containsKey("authPassphrase"));
    }

    @Test
    void metadataVersionIs020() {
        assertEquals("0.2.0", new SnmpDeviceDriver().metadata().version());
    }
}
