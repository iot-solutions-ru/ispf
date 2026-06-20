package com.ispf.driver.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpPointTest {

    @Test
    void parsesMethodAndRelativePath() {
        HttpPoint point = HttpPoint.parse("GET:/api/v1/info", "http://localhost:8080");
        assertEquals("GET", point.method());
        assertEquals("http://localhost:8080/api/v1/info", point.url());
    }

    @Test
    void parsesFullUrl() {
        HttpPoint point = HttpPoint.parse("https://example.com/health", "http://localhost:8080");
        assertEquals("https://example.com/health", point.url());
    }

    @Test
    void parsesJsonSuffix() {
        HttpPoint point = HttpPoint.parse("/status:json", "http://localhost:8080");
        assertTrue(point.parseJsonBody());
        assertEquals("http://localhost:8080/status", point.url());
    }
}
