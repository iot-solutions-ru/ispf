package com.ispf.server.driver;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DriverBindingBlankIdTest {

    @Test
    void blankDriverIdIsRejected() {
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class, () ->
                DriverBinding.of("", 2000, Map.of(), Map.of()));
        IllegalArgumentException spaces = assertThrows(IllegalArgumentException.class, () ->
                DriverBinding.of(" ", 2000, Map.of(), Map.of()));

        assertEquals("driverId must not be blank", empty.getMessage());
        assertEquals("driverId must not be blank", spaces.getMessage());
        assertEquals("virtual", DriverBinding.of("virtual", 2000, Map.of(), Map.of()).driverId());
    }
}
