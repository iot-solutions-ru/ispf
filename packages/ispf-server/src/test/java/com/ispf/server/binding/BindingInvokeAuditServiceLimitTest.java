package com.ispf.server.binding;

import com.ispf.server.application.data.PlatformSqlCatalog;
import com.ispf.server.config.BindingProperties;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingInvokeAuditServiceLimitTest {

    private JdbcTemplate jdbcTemplate;
    private BindingInvokeAuditService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        PlatformSqlCatalog catalog = mock(PlatformSqlCatalog.class);
        when(catalog.table("binding_invoke_audit")).thenReturn("binding_invoke_audit");
        service = new BindingInvokeAuditService(
                jdbcTemplate,
                catalog,
                mock(BindingProperties.class),
                mock(ObjectManager.class)
        );
    }

    @Test
    void zeroLimitIsAnEmptyPage() {
        assertThat(service.list(null, null, null, null, null, 0)).isEmpty();
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void negativeLimitIsRejected() {
        assertThatThrownBy(() -> service.list(null, null, null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Binding invocation limit must not be negative");
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void positiveLimitIsPassedToTheQuery() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        assertThat(service.list(null, null, null, null, null, 5)).isEmpty();
        verify(jdbcTemplate).query(contains("LIMIT ?"), any(RowMapper.class), any(Object[].class));
    }
}
