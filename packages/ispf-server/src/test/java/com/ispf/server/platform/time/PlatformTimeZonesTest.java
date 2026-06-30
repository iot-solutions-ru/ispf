package com.ispf.server.platform.time;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformTimeZonesTest {

    @Test
    void normalizesUtcAliases() {
        assertThat(PlatformTimeZones.normalizeOrDefault(null)).isEqualTo("UTC");
        assertThat(PlatformTimeZones.normalize("utc")).isEqualTo("UTC");
        assertThat(PlatformTimeZones.normalize("Europe/Moscow")).isEqualTo("Europe/Moscow");
    }

    @Test
    void rejectsInvalidZone() {
        assertThatThrownBy(() -> PlatformTimeZones.normalize("Not/A/Zone"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
