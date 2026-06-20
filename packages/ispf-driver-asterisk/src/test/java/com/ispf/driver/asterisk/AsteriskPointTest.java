package com.ispf.driver.asterisk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AsteriskPointTest {

    @Test
    void formatsAmiMessage() {
        AsteriskPoint point = AsteriskPoint.parse("Action: Ping");
        assertTrue(point.toAmiMessage().contains("Action: Ping"));
        assertTrue(point.toAmiMessage().endsWith("\r\n\r\n"));
    }
}
