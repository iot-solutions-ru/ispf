package com.ispf.server.query;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectQueryHistorianColumnResolverTest {

    @Test
    void parseWindowDefaultsToFifteenMinutes() {
        assertThat(ObjectQueryHistorianColumnResolver.parseWindow(null)).isEqualTo(Duration.ofMinutes(15));
        assertThat(ObjectQueryHistorianColumnResolver.parseWindow("")).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void parseWindowAcceptsBucketSpecs() {
        assertThat(ObjectQueryHistorianColumnResolver.parseWindow("1h")).isEqualTo(Duration.ofHours(1));
        assertThat(ObjectQueryHistorianColumnResolver.parseWindow("5m")).isEqualTo(Duration.ofMinutes(5));
    }
}
