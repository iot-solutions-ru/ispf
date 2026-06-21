package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectFieldBindingTest {

    @Test
    void matchesSyntax() {
        assertTrue(SelectFieldBinding.INSTANCE.matches("selectField(temperature)"));
        assertTrue(SelectFieldBinding.INSTANCE.matches("selectField(temperature, unit)"));
    }
}
