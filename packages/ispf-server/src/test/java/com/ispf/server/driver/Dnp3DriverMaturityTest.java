package com.ispf.server.driver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Dnp3DriverMaturityTest {

    @Test
    void maturityIsBeta() {
        assertEquals(com.ispf.driver.DriverMaturity.BETA, DriverMaturityRegistry.resolve("dnp3"));
    }
}
