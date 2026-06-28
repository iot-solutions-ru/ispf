package com.ispf.driver;

import java.util.Map;
import java.util.Set;

/**
 * Describes a loadable device driver plugin.
 */
public record DriverMetadata(
        String id,
        String name,
        String version,
        String description,
        String vendor,
        Map<String, String> configurationSchema,
        DriverMaturity maturity,
        Set<String> capabilities
) {
    public DriverMetadata(
            String id,
            String name,
            String version,
            String description,
            String vendor,
            Map<String, String> configurationSchema
    ) {
        this(id, name, version, description, vendor, configurationSchema, DriverMaturity.PRODUCTION, Set.of("read"));
    }

    public DriverMetadata(
            String id,
            String name,
            String version,
            String description,
            String vendor,
            Map<String, String> configurationSchema,
            DriverMaturity maturity
    ) {
        this(id, name, version, description, vendor, configurationSchema, maturity, Set.of("read"));
    }

    public DriverMetadata {
        if (maturity == null) {
            maturity = DriverMaturity.PRODUCTION;
        }
        if (capabilities == null || capabilities.isEmpty()) {
            capabilities = Set.of("read");
        }
    }

    public DriverMetadata withMaturity(DriverMaturity nextMaturity) {
        return new DriverMetadata(id, name, version, description, vendor, configurationSchema, nextMaturity, capabilities);
    }

    public DriverMetadata withCapabilities(Set<String> nextCapabilities) {
        return new DriverMetadata(id, name, version, description, vendor, configurationSchema, maturity, nextCapabilities);
    }

    public boolean supportsWrite() {
        return capabilities.contains("write");
    }
}
