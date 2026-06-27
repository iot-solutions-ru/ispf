package com.ispf.server.driver;

import com.ispf.server.driver.pack.LicensedDriverRegistry;
import com.ispf.server.driver.pack.DriverPackIndex;
import org.springframework.stereotype.Component;

@Component
public class DriverFactory {

    private final LicensedDriverRegistry licensedDriverRegistry;
    private final DriverPackIndex driverPackIndex;

    public DriverFactory(LicensedDriverRegistry licensedDriverRegistry, DriverPackIndex driverPackIndex) {
        this.licensedDriverRegistry = licensedDriverRegistry;
        this.driverPackIndex = driverPackIndex;
    }

    public com.ispf.driver.DeviceDriver create(String driverId) {
        if (licensedDriverRegistry.contains(driverId)) {
            return licensedDriverRegistry.create(driverId);
        }
        String packId = driverPackIndex.packIdFor(driverId).orElse(null);
        if (packId != null) {
            throw new IllegalArgumentException(
                    "Driver '" + driverId + "' is not installed. Install driver pack '" + packId + "' into ispf.driver.packs-dir.");
        }
        throw new IllegalArgumentException("Unknown driver: " + driverId);
    }
}
