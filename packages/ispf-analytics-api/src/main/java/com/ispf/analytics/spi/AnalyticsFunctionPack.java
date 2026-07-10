package com.ispf.analytics.spi;

import java.util.List;

/**
 * Manifest metadata for an analytics function pack.
 *
 * @param packId stable pack identifier
 * @param version semantic version of the pack
 * @param licenseType pack license designation
 * @param functions descriptors declared by the pack
 */
public record AnalyticsFunctionPack(
        String packId,
        String version,
        String licenseType,
        List<AnalyticsFunctionDescriptor> functions
) {
}
