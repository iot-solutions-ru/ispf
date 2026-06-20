package com.ispf.driver;

import java.util.Map;

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
        DriverMaturity maturity
) {
    public DriverMetadata(
            String id,
            String name,
            String version,
            String description,
            String vendor,
            Map<String, String> configurationSchema
    ) {
        this(id, name, version, description, vendor, configurationSchema, DriverMaturity.PRODUCTION);
    }

    public DriverMetadata {
        if (maturity == null) {
            maturity = DriverMaturity.PRODUCTION;
        }
    }

    public DriverMetadata withMaturity(DriverMaturity nextMaturity) {
        return new DriverMetadata(id, name, version, description, vendor, configurationSchema, nextMaturity);
    }
}
