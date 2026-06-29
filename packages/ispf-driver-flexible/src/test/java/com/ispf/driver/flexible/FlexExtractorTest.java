package com.ispf.driver.flexible;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlexExtractorTest {

    @Test
    void decodesManualFloatExamples() {
        assertFloat("3F800000", 1.0f);
        assertFloat("B8D1B717", -0.0001f, 0.00005f);
        assertFloat("C2C7FAE1", -99.99f, 0.01f);
        assertFloat("461C4000", 10000.0f);
    }

    @Test
    void extractsNthAsciiHexFloat() {
        String payload = "i20101250629150001R0000073F800000400000004080000040A0000";
        FlexExtractor extractor = FlexExtractor.parse("extract:asciiHexFloat:0:after:07");
        assertEquals("1.0", extractor.extract(payload));

        FlexExtractor height = FlexExtractor.parse("extract:asciiHexFloat:2:after:07");
        assertEquals("4.0", height.extract(payload));
    }

    @Test
    void extractsRegexGroup() {
        FlexExtractor extractor = FlexExtractor.parse("extract:regex:STATUS=(\\w+):1");
        assertEquals("OK", extractor.extract("PREFIX STATUS=OK SUFFIX"));
    }

    @Test
    void extractsSlice() {
        FlexExtractor extractor = FlexExtractor.parse("extract:slice:2:3");
        assertEquals("345", extractor.extract("123456"));
    }

    @Test
    void extractsLiteral() {
        FlexExtractor extractor = FlexExtractor.parse("extract:literal:42");
        assertEquals("42", extractor.extract("ignored"));
    }

    private static void assertFloat(String hex, float expected) {
        assertFloat(hex, expected, 0.001f);
    }

    private static void assertFloat(String hex, float expected, float delta) {
        float actual = Float.parseFloat(decode(hex));
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, delta);
    }

    private static String decode(String hex) {
        FlexExtractor extractor = FlexExtractor.parse("extract:asciiHexFloat:0");
        return extractor.extract(hex);
    }
}
