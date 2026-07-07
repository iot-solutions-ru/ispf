package com.ispf.server.config;

import com.ispf.server.tenant.TenantIsolationValidator;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIsolationPropertiesTest {

    @Test
    void defaultsToLogicalMode() {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        assertThat(properties.getIsolationMode()).isEqualTo(TenantIsolationProperties.IsolationMode.LOGICAL);
        assertThat(properties.isHardMode()).isFalse();
        assertThat(properties.schemaNameForTenant("acme")).isEqualTo("tenant_acme");
    }

    @Test
    void hardModeValidatesSchemaSafeTenantId() {
        TenantIsolationProperties properties = new TenantIsolationProperties();
        properties.setIsolationMode(TenantIsolationProperties.IsolationMode.HARD);
        TenantIsolationValidator validator = new TenantIsolationValidator(properties);

        assertThatCode(() -> validator.validateTenantIdForCreate("acme")).doesNotThrowAnyException();
        assertThat(validator.resolveSchemaName("acme")).isEqualTo("tenant_acme");

        assertThatThrownBy(() -> validator.validateTenantIdForCreate("ACME"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Hard mode tenantId");
    }
}
