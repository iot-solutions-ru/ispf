package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionPropertiesTest {

    @Test
    void javaSourceCompilationIsOffUnlessEnabled() {
        assertFalse(new FunctionProperties().javaFunctionsEnabled());
    }

    @Test
    void nestedJavaEnabledIsTheSwitch() {
        var source = new MapConfigurationPropertySource(Map.of("ispf.function.java.enabled", "true"));
        FunctionProperties bound = new Binder(source)
                .bind("ispf.function", FunctionProperties.class)
                .get();
        assertTrue(bound.javaFunctionsEnabled());
        assertTrue(bound.getJava().isEnabled());
    }
}
