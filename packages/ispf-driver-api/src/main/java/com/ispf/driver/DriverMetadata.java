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
        Map<String, String> configurationSchema
) {
}
