package com.ispf.server.config;

import com.ispf.server.tenant.TenantIsolationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantIsolationPropertiesTest {

    @Test
    void defaultsToLogicalMode() {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        assertThat(properties.getIsolationMode()).isEqualTo(TenantIsolationProperties.IsolationMode.LOGICAL);
        assertThat(properties.isHardMode()).isFalse();
        assertThat(properties.schemaNameForTenant("acme")).isEqualTo("tenant_acme");
        assertThat(properties.getOidcTenantClaim()).isEqualTo("tenant_id");
    }

    @Test
    void hardModeValidatesSchemaSafeTenantId() {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        properties.setIsolationMode(TenantIsolationProperties.IsolationMode.HARD);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        TenantIsolationValidator validator = new TenantIsolationValidator(properties, jdbc);

        assertThatCode(() -> validator.validateTenantIdForCreate("acme")).doesNotThrowAnyException();
        assertThat(validator.resolveSchemaName("acme")).isEqualTo("tenant_acme");

        assertThatThrownBy(() -> validator.validateTenantIdForCreate("ACME"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Hard mode tenantId");
    }

    @Test
    void hardModeRejectsExistingSchemaAndReservedNames() {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        properties.setIsolationMode(TenantIsolationProperties.IsolationMode.HARD);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("tenant_taken"))).thenReturn(1);
        TenantIsolationValidator validator = new TenantIsolationValidator(properties, jdbc);

        assertThatThrownBy(() -> validator.validateTenantIdForCreate("taken"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");

        assertThatThrownBy(() -> validator.rejectReservedSchema("public"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void normalizesOidcTenantClaim() {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        TenantIsolationValidator validator = new TenantIsolationValidator(properties, mock(JdbcTemplate.class));
        assertThat(validator.normalizeOidcTenantClaim("tenant:acme")).isEqualTo("acme");
        assertThat(validator.normalizeOidcTenantClaim("plant-1")).isEqualTo("plant-1");
    }
}
