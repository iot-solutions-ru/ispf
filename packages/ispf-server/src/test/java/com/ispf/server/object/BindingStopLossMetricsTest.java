package com.ispf.server.object;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;

class BindingStopLossMetricsTest {

    @Test
    void functionCounterTracksTruncationTotal() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        long before = BindingRuleEngine.truncationTotal();
        new BindingStopLossMetrics(provider(registry));

        assertThat(registry.get("ispf.binding.truncations").functionCounter().count())
                .isEqualTo((double) before);
    }

    @Test
    void prometheusScrapeExposesTotalSuffix() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new BindingStopLossMetrics(provider(registry));
        assertThat(registry.scrape()).contains("ispf_binding_truncations_total");
    }

    private static ObjectProvider<MeterRegistry> provider(MeterRegistry registry) {
        return new ObjectProvider<>() {
            @Override
            public MeterRegistry getObject() {
                return registry;
            }

            @Override
            public MeterRegistry getIfAvailable() {
                return registry;
            }

            @Override
            public MeterRegistry getIfUnique() {
                return registry;
            }
        };
    }
}
