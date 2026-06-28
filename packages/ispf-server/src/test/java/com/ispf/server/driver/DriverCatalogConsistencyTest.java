package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DriverCatalogConsistencyTest {

    @Autowired
    private DriverCatalog driverCatalog;

    @Test
    void catalogMergesMaturityAndCapabilities() {
        List<DriverMetadata> drivers = driverCatalog.list();
        assertFalse(drivers.isEmpty(), "driver catalog should not be empty in test profile");

        for (DriverMetadata driver : drivers) {
            assertNotNull(driver.maturity(), driver.id() + " maturity");
            assertNotNull(driver.capabilities(), driver.id() + " capabilities");
            assertTrue(driver.capabilities().contains("read"), driver.id() + " should declare read");

            DriverMaturity expectedMaturity = DriverMaturityRegistry.resolve(driver.id());
            assertEqualsMaturity(driver, expectedMaturity);

            Set<String> expectedCapabilities = DriverCapabilityRegistry.resolve(driver.id());
            assertEqualsCapabilities(driver, expectedCapabilities);
        }
    }

    private static void assertEqualsMaturity(DriverMetadata driver, DriverMaturity expected) {
        if (driver.maturity() != expected) {
            throw new AssertionError(driver.id() + " maturity expected " + expected + " but was " + driver.maturity());
        }
    }

    private static void assertEqualsCapabilities(DriverMetadata driver, Set<String> expected) {
        if (!driver.capabilities().equals(expected)) {
            throw new AssertionError(driver.id() + " capabilities expected " + expected + " but was " + driver.capabilities());
        }
    }
}
