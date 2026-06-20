package com.ispf.driver.flexible;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FlexiblePointTest {

    @Test
    void parsesRequestOnly() {
        FlexiblePoint point = FlexiblePoint.parse("PING");
        assertEquals("PING", point.request());
        assertNull(point.responseRegex());
    }

    @Test
    void parsesRequestWithRegex() {
        FlexiblePoint point = FlexiblePoint.parse("STATUS:OK=(\\d+)");
        assertEquals("STATUS", point.request());
        assertEquals(Pattern.compile("OK=(\\d+)").pattern(), point.responseRegex().pattern());
    }

    @Test
    void hexRoundTrip() {
        byte[] bytes = FlexibleDeviceDriver.hexToBytes("0102FF");
        assertEquals("0102FF", FlexibleDeviceDriver.bytesToHex(bytes, bytes.length));
    }
}
