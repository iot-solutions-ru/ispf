package com.ispf.server.tenant;

import com.ispf.server.config.TenantIsolationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared RLS filter bean (wired into security filter chains; servlet registration disabled).
 */
@Configuration
public class TenantRlsSecurityConfiguration {

    @Bean
    TenantRlsFilter tenantRlsFilter(
            TenantScopeService tenantScopeService,
            TenantIsolationProperties isolationProperties
    ) {
        return new TenantRlsFilter(tenantScopeService, isolationProperties);
    }

    @Bean
    FilterRegistrationBean<TenantRlsFilter> tenantRlsFilterRegistration(TenantRlsFilter filter) {
        FilterRegistrationBean<TenantRlsFilter> registration = new FilterRegistrationBean<>(filter);
        // Only run inside SecurityFilterChain (after auth filters), not as a servlet filter.
        registration.setEnabled(false);
        return registration;
    }
}
