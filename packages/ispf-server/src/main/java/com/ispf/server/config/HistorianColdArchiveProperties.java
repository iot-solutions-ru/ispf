package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Scheduled cold-tier Parquet export (BL-202).
 */
@ConfigurationProperties(prefix = "ispf.historian.cold-archive")
public record HistorianColdArchiveProperties(
        @DefaultValue("false") boolean enabled,
        /** Local directory root; files are written under {@code {localRoot}/{bucket}/{prefix}/}. */
        @DefaultValue("") String localRoot,
        @DefaultValue("200") int maxSeriesPerRun,
        @DefaultValue("10000") int maxSamplesPerSeries
) {
}
