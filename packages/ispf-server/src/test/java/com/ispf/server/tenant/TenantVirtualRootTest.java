package com.ispf.server.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantVirtualRootTest {

    @Test
    void expandsAndCollapsesPlatformSubtree() {
        assertThat(TenantVirtualRoot.toCanonical("root.platform", "acme"))
                .isEqualTo("root.tenant.acme.platform");
        assertThat(TenantVirtualRoot.toCanonical("root.platform.devices.pump", "acme"))
                .isEqualTo("root.tenant.acme.platform.devices.pump");
        assertThat(TenantVirtualRoot.toCanonical("root", "acme")).isEqualTo("root");
        assertThat(TenantVirtualRoot.toCanonical("root.tenant.acme.platform.devices", "acme"))
                .isEqualTo("root.tenant.acme.platform.devices");

        assertThat(TenantVirtualRoot.toVirtual("root.tenant.acme.platform", "acme"))
                .isEqualTo("root.platform");
        assertThat(TenantVirtualRoot.toVirtual("root.tenant.acme.platform.devices.pump", "acme"))
                .isEqualTo("root.platform.devices.pump");
        assertThat(TenantVirtualRoot.toVirtual("root.tenant", "acme")).isNull();
        assertThat(TenantVirtualRoot.toVirtual("root.tenant.acme", "acme")).isNull();
        assertThat(TenantVirtualRoot.toVirtual("root", "acme")).isEqualTo("root");
    }
}
