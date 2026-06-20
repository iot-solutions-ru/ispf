package com.ispf.driver.smb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmbPointTest {

    @Test
    void normalizesLeadingSlashes() {
        SmbPoint point = SmbPoint.parse("/reports/daily.csv");
        assertEquals("reports/daily.csv", point.filePath());
    }
}
