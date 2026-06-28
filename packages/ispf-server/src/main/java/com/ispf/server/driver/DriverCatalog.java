package com.ispf.server.driver;

import com.ispf.driver.DriverMetadata;
import com.ispf.server.driver.pack.LicensedDriverRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverCatalog {

    private final LicensedDriverRegistry licensedDriverRegistry;

    public DriverCatalog(LicensedDriverRegistry licensedDriverRegistry) {
        this.licensedDriverRegistry = licensedDriverRegistry;
    }

    public List<DriverMetadata> list() {
        return licensedDriverRegistry.metadata().stream()
                .map(driver -> {
                    String id = driver.id();
                    return driver
                            .withMaturity(DriverMaturityRegistry.resolve(id))
                            .withCapabilities(DriverCapabilityRegistry.resolve(id));
                })
                .toList();
    }
}
