package com.ispf.server.config;

import com.ispf.driver.ingress.IngressElasticSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterPropertiesScheduledPoolTest {

    @Test
    void scheduledPoolElasticDefaultsToMinTwoMaxEight() {
        IngressElasticSettings elastic = cluster(true, 2, 8).resolvedScheduledPoolElastic();
        assertTrue(elastic.enabled());
        assertEquals(2, elastic.resolvedMinWorkers());
        assertEquals(8, elastic.resolvedMaxWorkers());
    }

    @Test
    void scheduledPoolFixedWhenElasticDisabled() {
        IngressElasticSettings elastic = cluster(false, 2, 8).resolvedScheduledPoolElastic();
        assertEquals(8, elastic.resolvedMinWorkers());
        assertEquals(8, elastic.resolvedMaxWorkers());
    }

    private static ClusterProperties cluster(boolean elasticEnabled, int min, int max) {
        return new ClusterProperties(
                true,
                true,
                30,
                10,
                15,
                10,
                30,
                elasticEnabled,
                min,
                max,
                4,
                6,
                500,
                true,
                true,
                500,
                true,
                "",
                "",
                "all",
                true,
                2000,
                2,
                true,
                1,
                8,
                50,
                6,
                500,
                1800
        );
    }
}
