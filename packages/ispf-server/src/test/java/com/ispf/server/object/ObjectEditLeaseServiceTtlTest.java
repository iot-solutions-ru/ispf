package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ObjectEditLeaseServiceTtlTest {

    @Test
    void rejectsNonPositiveTtlBeforeInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectEditLeaseService service = new ObjectEditLeaseService(jdbcTemplate);

        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class, () ->
                service.acquire("root.platform.devices.lease-proof", "admin", Duration.ofMinutes(0)));
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () ->
                service.acquire("root.platform.devices.lease-proof", "admin", Duration.ofMinutes(-1)));

        assertEquals("Edit lease ttl must be positive", zero.getMessage());
        assertEquals("Edit lease ttl must be positive", negative.getMessage());
        verifyNoInteractions(jdbcTemplate);
    }
}
