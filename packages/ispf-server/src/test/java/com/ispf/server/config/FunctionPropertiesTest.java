package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FunctionPropertiesTest {

    @Test
    void javaSourceCompilationIsOffUnlessEnabled() {
        assertFalse(new FunctionProperties().isJavaEnabled());
    }
}
