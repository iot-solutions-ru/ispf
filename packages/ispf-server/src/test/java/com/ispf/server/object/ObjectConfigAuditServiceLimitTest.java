package com.ispf.server.object;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectConfigAuditServiceLimitTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void zeroLimitIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().list("root.platform.devices.room", 0)
        );

        assertEquals("Audit limit must be positive", exception.getMessage());
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(), any());
    }

    @Test
    void negativeLimitIsRejected() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service().list("root.platform.devices.room", -2)
        );

        assertEquals("Audit limit must be positive", exception.getMessage());
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(), any());
    }

    @Test
    void positiveLimitIsCappedAndQueried() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("root.platform.devices.room"), eq(5)))
                .thenReturn(List.of());

        assertEquals(List.of(), service().list("root.platform.devices.room", 5));
        verify(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                eq("root.platform.devices.room"),
                eq(5)
        );
    }

    private ObjectConfigAuditService service() {
        return new ObjectConfigAuditService(jdbcTemplate, new com.ispf.server.relational.H2Dialect());
    }
}
