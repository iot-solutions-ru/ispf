package com.ispf.driver.vmware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VmwarePointTest {

    @Test
    void parsesPropertyPath() {
        VmwarePoint point = VmwarePoint.parse("about.version");
        assertEquals("about.version", point.propertyPath());
        assertTrue(!point.isConnectedPoint());
    }

    @Test
    void parsesConnectedStatus() {
        VmwarePoint point = VmwarePoint.parse("connected");
        assertTrue(point.isConnectedPoint());
        assertEquals("connected", point.propertyPath());
    }
}
