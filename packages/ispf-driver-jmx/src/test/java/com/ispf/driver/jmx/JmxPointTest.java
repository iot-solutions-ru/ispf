package com.ispf.driver.jmx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JmxPointTest {

    @Test
    void parsesDoubleColonWithCompositeKey() {
        JmxPoint point = JmxPoint.parse("java.lang:type=Memory::HeapMemoryUsage.used");
        assertEquals("java.lang:type=Memory", point.objectName());
        assertEquals("HeapMemoryUsage", point.attribute());
        assertEquals("used", point.compositeKey());
    }

    @Test
    void parsesSimpleColonFormat() {
        JmxPoint point = JmxPoint.parse("java.lang:type=Memory:HeapMemoryUsage:used");
        assertEquals("java.lang:type=Memory", point.objectName());
        assertEquals("HeapMemoryUsage", point.attribute());
        assertEquals("used", point.compositeKey());
    }

    @Test
    void parsesAttributeWithoutCompositeKey() {
        JmxPoint point = JmxPoint.parse("java.lang:type=Runtime::Uptime");
        assertEquals("Uptime", point.attribute());
        assertNull(point.compositeKey());
    }
}
