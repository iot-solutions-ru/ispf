package com.ispf.server.driver;

import com.ispf.driver.DriverMaturity;
import com.ispf.driver.DriverMetadata;

import java.util.Map;
import java.util.Set;

/**
 * Catalog row for {@code GET /api/v1/drivers} and {@code list_drivers}.
 * Same fields as {@link DriverMetadata}, plus the evidence lane and an explicit read-only flag.
 */
public record DriverListing(
        String id,
        String name,
        String version,
        String description,
        String vendor,
        Map<String, String> configurationSchema,
        DriverMaturity maturity,
        Set<String> capabilities,
        DriverEvidence evidence,
        boolean readOnly
) {
    public static DriverListing of(DriverMetadata metadata) {
        Set<String> capabilities = metadata.capabilities();
        return new DriverListing(
                metadata.id(),
                metadata.name(),
                metadata.version(),
                metadata.description(),
                metadata.vendor(),
                metadata.configurationSchema(),
                metadata.maturity(),
                capabilities,
                DriverEvidenceRegistry.resolve(metadata.id()),
                !metadata.supportsWrite()
        );
    }
}
