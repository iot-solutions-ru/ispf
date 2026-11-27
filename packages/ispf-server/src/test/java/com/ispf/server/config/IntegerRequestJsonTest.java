package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Documents the {@code ACCEPT_FLOAT_AS_INT=false} contract used by Spring HTTP JSON.
 * The Spring Boot key is covered by {@link IntegerRequestLeaseApiTest}.
 */
class IntegerRequestJsonTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();

    @Test
    void fractionalIntegerIsRejectedAndWholeNumberIsKept() {
        assertThatThrownBy(() -> mapper.readValue("{\"ttlMinutes\":1.5}", Minutes.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("{\"ttlMinutes\":2.0}", Minutes.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("{\"ttlMinutes\":1e1}", Minutes.class))
                .isInstanceOf(Exception.class);
        assertThat(mapper.readValue("{\"ttlMinutes\":2}", Minutes.class).ttlMinutes()).isEqualTo(2);

        assertThatThrownBy(() -> mapper.readValue("{\"delaySeconds\":1.5}", Delay.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> mapper.readValue("{\"delaySeconds\":2.0}", Delay.class))
                .isInstanceOf(Exception.class);
        assertThat(mapper.readValue("{\"delaySeconds\":2}", Delay.class).delaySeconds()).isEqualTo(2);
    }

    private record Minutes(Integer ttlMinutes) {
    }

    private record Delay(Integer delaySeconds) {
    }
}
