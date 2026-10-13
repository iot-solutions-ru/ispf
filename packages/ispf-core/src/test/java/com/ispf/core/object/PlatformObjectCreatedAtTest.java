package com.ispf.core.object;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformObjectCreatedAtTest {

    @Test
    void reconstructPreservesCreatedAtFromPersistence() {
        Instant created = Instant.parse("2020-01-02T03:04:05Z");
        PlatformObject node = new PlatformObject(
                "id-1",
                "root.platform.devices.a",
                ObjectType.DEVICE,
                "A",
                "",
                null,
                0,
                3L,
                "admin",
                Instant.parse("2021-01-01T00:00:00Z"),
                created
        );
        assertThat(node.createdAt()).isEqualTo(created);
    }
}
