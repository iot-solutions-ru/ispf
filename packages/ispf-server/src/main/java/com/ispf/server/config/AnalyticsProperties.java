package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ispf.analytics")
public record AnalyticsProperties(
        @DefaultValue("60000") long derivedTagTickMs,
        @DefaultValue("true") boolean derivedTagEnabled,
        @DefaultValue("true") boolean engineEnabled,
        @DefaultValue("60000") long enginePeriodicMs,
        @DefaultValue("false") boolean materializerEnabled,
        @DefaultValue("60000") long materializerTickMs,
        @DefaultValue("7") int rollupMinQueryRangeDays,
        @DefaultValue("20") int queryMaxTags,
        @DefaultValue("3000") long queryTimeoutMs,
        @DefaultValue("600") int queryRateLimitPerMinute
) {
}
