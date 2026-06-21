package com.ispf.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatBindingTest {

    @Test
    void matchesSyntax() {
        assertTrue(FormatBinding.INSTANCE.matches("format(\"%.1f\", temperature)"));
        assertTrue(FormatBinding.INSTANCE.matches("format(\"%s\", source, unit)"));
    }
}
