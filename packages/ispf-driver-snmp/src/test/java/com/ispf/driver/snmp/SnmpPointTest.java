package com.ispf.driver.snmp;

import com.ispf.core.model.DataRecord;
import org.junit.jupiter.api.Test;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OctetString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnmpPointTest {

    @Test
    void parsesPlainOid() throws Exception {
        SnmpPoint point = SnmpPoint.parse("1.3.6.1.2.1.1.5.0");
        assertEquals("1.3.6.1.2.1.1.5.0", point.oid());
        assertEquals(SnmpPoint.ValueKind.AUTO, point.valueKind());
        assertFalse(point.optional());
    }

    @Test
    void parsesOidWithValueKind() throws Exception {
        SnmpPoint point = SnmpPoint.parse("1.3.6.1.2.1.1.5.0:STRING");
        assertEquals(SnmpPoint.ValueKind.STRING, point.valueKind());
        assertFalse(point.optional());
    }

    @Test
    void parsesOptionalOidWithValueKind() throws Exception {
        SnmpPoint point = SnmpPoint.parse("1.3.6.1.2.1.25.3.3.1.2.1:INTEGER:optional");
        assertEquals("1.3.6.1.2.1.25.3.3.1.2.1", point.oid());
        assertEquals(SnmpPoint.ValueKind.INTEGER, point.valueKind());
        assertTrue(point.optional());
    }

    @Test
    void parsesOptionalOidWithoutValueKind() throws Exception {
        SnmpPoint point = SnmpPoint.parse("1.3.6.1.2.1.1.5.0:optional");
        assertEquals(SnmpPoint.ValueKind.AUTO, point.valueKind());
        assertTrue(point.optional());
    }

    @Test
    void rejectsInvalidMapping() {
        assertThrows(Exception.class, () -> SnmpPoint.parse(" "));
    }
}
