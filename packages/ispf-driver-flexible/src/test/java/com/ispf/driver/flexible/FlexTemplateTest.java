package com.ispf.driver.flexible;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlexTemplateTest {

    @Test
    void rendersEscapesAndVariables() {
        byte[] bytes = FlexTemplate.render("\\x01i201${tank}", Map.of("tank", "01"));
        assertArrayEquals(new byte[] {0x01, 'i', '2', '0', '1', '0', '1'}, bytes);
    }

    @Test
    void rendersMissingVariableAsEmpty() {
        byte[] bytes = FlexTemplate.render("\\x01${securityCode}OK", Map.of());
        assertArrayEquals(new byte[] {0x01, 'O', 'K'}, bytes);
    }

    @Test
    void producesStableGroupKey() {
        FlexExchangePoint point = FlexExchangePoint.parse("req:\\x01${code}PING|extract:literal:1");
        String keyA = point.requestGroupKey(Map.of("code", "X"));
        String keyB = point.requestGroupKey(Map.of("code", "X"));
        assertEquals(keyA, keyB);
    }
}
