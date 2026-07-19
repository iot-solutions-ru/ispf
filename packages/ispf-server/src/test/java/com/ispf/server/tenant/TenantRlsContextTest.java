package com.ispf.server.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRlsContextTest {

    @AfterEach
    void clear() {
        TenantRlsContext.clear();
    }

    @Test
    void unsetReturnsNull() {
        assertThat(TenantRlsContext.get()).isNull();
    }

    @Test
    void setAndClearRoundTrip() {
        TenantRlsContext.setTenant("acme");
        assertThat(TenantRlsContext.get().bypass()).isFalse();
        assertThat(TenantRlsContext.get().tenantId()).isEqualTo("acme");

        TenantRlsContext.setBypass();
        assertThat(TenantRlsContext.get().bypass()).isTrue();
        assertThat(TenantRlsContext.get().tenantId()).isEmpty();

        TenantRlsContext.clear();
        assertThat(TenantRlsContext.get()).isNull();
    }
}
