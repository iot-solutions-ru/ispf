package com.ispf.server.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

public final class IspfAuthorizationRules {

    private IspfAuthorizationRules() {
    }

    public static void apply(
            AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth.requestMatchers(
                "/api/v1/info",
                "/api/v1/auth/login",
                "/api/v1/auth/config",
                "/api/v1/auth/me",
                "/actuator/health",
                "/ws/**"
        ).permitAll();

        auth.requestMatchers("/actuator/prometheus")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        auth.requestMatchers("/api/v1/platform/metrics", "/api/v1/platform/function-invocations",
                        "/api/v1/platform/function-audit-status",
                        "/api/v1/platform/binding-invocations", "/api/v1/platform/binding-audit-status",
                        "/api/v1/platform/installation-id", "/api/v1/platform/license",
                        "/api/v1/platform/automation-index/**",
                        "/api/v1/platform/redis/**", "/api/v1/platform/nats/**",
                        "/api/v1/platform/cluster/**",
                        "/api/v1/platform/diagnostics/**",
                        "/api/v1/platform/storage/**",
                        "/api/v1/platform/reports/yarg/**", "/api/v1/platform/mcp/**",
                        "/api/v1/platform/backup/**",
                        "/api/v1/platform/runtime-settings")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        auth.requestMatchers(HttpMethod.GET, "/api/v1/ai/provider")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers("/api/v1/ai/**")
                .hasAnyRole(IspfRoles.ROLES_CONFIG);

        auth.requestMatchers("/api/v1/operator-apps/*/agent/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers("/api/v1/platform/update/**")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        auth.requestMatchers("/api/v1/alert-rules/**", "/api/v1/correlators/**",
                        "/api/v1/event-filters/**",
                        "/api/v1/data-sources/**", "/api/v1/migrations/**", "/api/v1/sql-bindings/**",
                        "/api/v1/queries/**")
                .hasAnyRole(IspfRoles.ROLES_CONFIG);

        auth.requestMatchers("/api/v1/work-queue/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/objects/by-path/functions/invoke")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/bff/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.GET, "/api/v1/applications/*/operator-ui")
                .hasAnyRole(IspfRoles.ROLES_READ);
        auth.requestMatchers(HttpMethod.GET, "/api/v1/applications/*/hmi-ui")
                .hasAnyRole(IspfRoles.ROLES_READ);
        auth.requestMatchers(HttpMethod.GET, "/api/v1/applications/*/operator-manifest")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.GET, "/api/v1/operator-apps")
                .hasAnyRole(IspfRoles.ROLES_READ);
        auth.requestMatchers(HttpMethod.GET, "/api/v1/operator-apps/*/ui")
                .hasAnyRole(IspfRoles.ROLES_READ);
        auth.requestMatchers(HttpMethod.POST, "/api/v1/operator-apps/**")
                .hasAnyRole(IspfRoles.ROLES_CONFIG);
        auth.requestMatchers(HttpMethod.PUT, "/api/v1/operator-apps/**")
                .hasAnyRole(IspfRoles.ROLES_CONFIG);

        auth.requestMatchers("/api/v1/alarm-shelves/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers("/api/v1/security/mfa/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers("/api/v1/security/**")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        auth.requestMatchers("/api/v1/federation/**")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        auth.requestMatchers("/api/v1/tenants/**")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        auth.requestMatchers("/api/v1/audit/**")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/alarm-shelves/requests/*/approve")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/alarm-shelves/requests/*/reject")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        auth.requestMatchers(HttpMethod.GET, "/api/v1/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/events/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/workflows/by-path/run")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/workflows/instances/*/cancel")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/workflows/instances/*/signal")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/workflows/instances/*/timer")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/workflows/signal")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/applications/*/reports/*/run")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/reports/by-path/run")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/reports/by-path/run-async")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/platform/analytics/expression/evaluate",
                        "/api/v1/platform/analytics/expression/validate",
                        "/api/v1/platform/analytics/catalog/validate",
                        "/api/v1/platform/analytics/query",
                        "/api/v1/platform/analytics/query/export")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.GET, "/api/v1/platform/jobs/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.PUT, "/api/v1/objects/by-path/acl")
                .hasAnyRole(IspfRoles.ROLES_ADMIN);

        // Object variable writes: operators allowed at HTTP layer; per-object ACL enforced in ObjectController.
        auth.requestMatchers(HttpMethod.PUT, "/api/v1/objects/by-path/variables")
                .hasAnyRole(IspfRoles.ROLES_READ);
        auth.requestMatchers(HttpMethod.PATCH, "/api/v1/objects/by-path/variables/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        // Binding rules: same as variables — operator HTTP access; per-object ACL in BindingRulesController.
        auth.requestMatchers(HttpMethod.PUT, "/api/v1/objects/by-path/binding-rules")
                .hasAnyRole(IspfRoles.ROLES_READ);
        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/objects/by-path/binding-rules/**")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers(HttpMethod.PUT, "/api/v1/auth/me/timezone")
                .hasAnyRole(IspfRoles.ROLES_READ);

        auth.requestMatchers("/api/v1/**").hasAnyRole(IspfRoles.ROLES_CONFIG);
    }
}
