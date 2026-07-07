package com.ispf.server.driver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Dnp3DriverMaturityTest {

    @Test
    void maturityIsProduction() {
        assertEquals(com.ispf.driver.DriverMaturity.PRODUCTION, DriverMaturityRegistry.resolve("dnp3"));
    }
}
