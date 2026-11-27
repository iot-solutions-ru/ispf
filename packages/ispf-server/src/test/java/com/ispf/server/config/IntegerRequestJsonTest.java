package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegerRequestJsonTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();

    @Test
    void fractionalIntegerIsRejectedAndWholeNumberIsKept() {
        assertThatThrownBy(() -> mapper.readValue("{\"ttlMinutes\":1.5}", Minutes.class))
                .hasMessageContaining("Cannot coerce Floating-point value");
        assertThat(mapper.readValue("{\"ttlMinutes\":2}", Minutes.class).ttlMinutes()).isEqualTo(2);
        assertThatThrownBy(() -> mapper.readValue("{\"delaySeconds\":1.5}", Delay.class))
                .hasMessageContaining("Cannot coerce Floating-point value");
        assertThat(mapper.readValue("{\"delaySeconds\":2}", Delay.class).delaySeconds()).isEqualTo(2);
    }

    private record Minutes(Integer ttlMinutes) {
    }

    private record Delay(Integer delaySeconds) {
    }
}
