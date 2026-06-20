package com.ispf.driver.opcua;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpcUaPointTest {

    @Test
    void parsesStringNodeId() throws Exception {
        OpcUaPoint point = OpcUaPoint.parse("ns=2;s=Temperature");
        assertEquals("Temperature", point.nodeId().getIdentifier().toString());
        assertEquals(2, point.nodeId().getNamespaceIndex().intValue());
    }

    @Test
    void parsesNumericNodeId() throws Exception {
        OpcUaPoint point = OpcUaPoint.parse("i=2258");
        assertEquals("2258", point.nodeId().getIdentifier().toString());
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(Exception.class, () -> OpcUaPoint.parse(""));
    }
}
