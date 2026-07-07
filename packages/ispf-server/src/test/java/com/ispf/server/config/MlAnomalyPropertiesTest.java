package com.ispf.server.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MlAnomalyPropertiesTest {

    @Test
    void defaultsDisabledWithThreshold() {
        MlAnomalyProperties properties = new MlAnomalyProperties();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDefaultThreshold()).isEqualTo(0.85);
    }
}
