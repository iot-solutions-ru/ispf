package com.ispf.server.persistence;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.persistence.entity.ObjectNodeEntity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ObjectEntityMapperCreatedAtTest {

    private final ObjectEntityMapper mapper = new ObjectEntityMapper(new ObjectMapper());

    @Test
    void copyNodeFieldsSetsCreatedAtOnNewEntityOnly() {
        Instant original = Instant.parse("2019-06-01T12:00:00Z");
        PlatformObject node = new PlatformObject(
                "id-1",
                "root.platform.devices.a",
                ObjectType.DEVICE,
                "A",
                "",
                null,
                0,
                1L,
                "admin",
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:00:00Z")
        );

        ObjectNodeEntity existing = new ObjectNodeEntity();
        existing.setId(node.id());
        existing.setCreatedAt(original);
        mapper.copyNodeFields(existing, node);
        assertEquals(original, existing.getCreatedAt());

        ObjectNodeEntity created = mapper.toEntity(node);
        assertEquals(node.createdAt(), created.getCreatedAt());
        assertNotEquals(original, created.getCreatedAt());
    }
}
