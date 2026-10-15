package com.ispf.server.operator;

import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.uipack.HostedUiPackLinkEnricher;
import com.ispf.server.config.BootstrapProperties;
import com.ispf.server.tenant.TenantScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperatorAppDashboardsJsonTest {

    @Mock
    OperatorAppUiStore store;
    @Mock
    ApplicationDataStore applicationDataStore;
    @Mock
    ApplicationBundleDeployService bundleDeployService;
    @Mock
    OperatorAppObjectTreeService objectTreeService;
    @Mock
    BootstrapProperties bootstrapProperties;
    @Mock
    TenantScopeService tenantScopeService;
    @Mock
    HostedUiPackLinkEnricher hostedUiPackLinkEnricher;

    private OperatorAppUiService service;
    private Authentication tenantAdmin;

    @BeforeEach
    void setUp() {
        service = new OperatorAppUiService(
                store,
                applicationDataStore,
                bundleDeployService,
                objectTreeService,
                new ObjectMapper(),
                bootstrapProperties,
                tenantScopeService,
                hostedUiPackLinkEnricher
        );
        tenantAdmin = new UsernamePasswordAuthenticationToken("acme-admin", "n/a");
        when(tenantScopeService.resolveTenantId(tenantAdmin)).thenReturn(Optional.of("acme"));
    }

    @Test
    void corruptDashboardsJsonIsNotTreatedAsOutsideTheTenant() {
        when(store.listAll()).thenReturn(List.of(record("{not-json")));

        assertThatThrownBy(() -> service.listApps(tenantAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid operator app dashboards JSON for plant");
    }

    @Test
    void emptyDashboardsStayOutsideTheTenant() {
        when(store.listAll()).thenReturn(List.of(record("[]")));

        assertThat(service.listApps(tenantAdmin)).isEmpty();
    }

    @Test
    void dashboardUnderTheTenantIsListed() {
        when(store.listAll()).thenReturn(List.of(record(
                "[{\"path\":\"root.tenant.acme.platform.dashboards.main\",\"title\":\"Main\"}]"
        )));

        assertThat(service.listApps(tenantAdmin)).hasSize(1);
    }

    private static OperatorAppUiStore.OperatorAppUiRecord record(String dashboardsJson) {
        return new OperatorAppUiStore.OperatorAppUiRecord(
                "plant",
                "Plant",
                "root.platform.dashboards.other",
                dashboardsJson,
                null,
                Instant.parse("2026-10-09T00:00:00Z")
        );
    }
}
