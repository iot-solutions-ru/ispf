package com.ispf.server.federation;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.tenant.DelegatedTenantAuthenticationDetails;
import com.ispf.server.tenant.TenantScopeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpRequest;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Shared on-behalf-of principal handling for federation transports.
 *
 * <p>The HTTP path is an untrusted delegation boundary: delegated roles are intersected with
 * the authenticated channel roles, and a delegated tenant must match the channel tenant unless
 * the channel principal is a global administrator. The tunnel path is different: its WebSocket
 * peer has already authenticated the channel, so {@link #installFromTrustedChannel} installs the
 * forwarded roles as-is after normalization. In particular, the trusted tunnel path intentionally
 * permits an empty role list so object member ACLs can authorize the delegated user.</p>
 */
public final class FederationDelegatedPrincipal {

    public static final String HEADER_ON_BEHALF_USER = "X-ISPF-On-Behalf-Of-User";
    public static final String HEADER_ON_BEHALF_ROLES = "X-ISPF-On-Behalf-Of-Roles";
    public static final String HEADER_ON_BEHALF_TENANT = "X-ISPF-On-Behalf-Of-Tenant";

    private FederationDelegatedPrincipal() {
    }

    public record Snapshot(String username, List<String> roles, String tenantId) {

        public Snapshot {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public static Snapshot capture(Authentication authentication, TenantScopeService tenants) {
        if (!isNamedPrincipal(authentication)) {
            return null;
        }
        List<String> roles = IspfRoles.extractRoles(authentication).stream()
                .map(FederationDelegatedPrincipal::normalizeRole)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        String tenantId = Objects.requireNonNull(tenants, "tenants")
                .resolveTenantId(authentication)
                .orElse(null);
        return new Snapshot(authentication.getName().trim(), roles, tenantId);
    }

    /**
     * Installs delegation received over an authenticated HTTP federation channel.
     *
     * <p>Claimed roles must be present and are reduced to their exact intersection with the
     * channel principal's roles. The channel principal is retained only as an authorization
     * ceiling; the returned principal's username is the delegated username.</p>
     */
    public static Authentication installFromHttpChannel(
            Authentication channel,
            String onBehalfUser,
            Collection<String> onBehalfRoles,
            String onBehalfTenant,
            TenantScopeService tenants
    ) {
        if (!isNamedPrincipal(channel)) {
            throw forbidden("Authenticated federation channel required");
        }
        String username = requireDelegatedUsername(onBehalfUser);
        LinkedHashSet<String> claimedRoles = normalizeRoles(onBehalfRoles);
        if (claimedRoles.isEmpty()) {
            throw forbidden("On-behalf-of roles are required");
        }

        LinkedHashSet<String> channelRoles = normalizeRoles(IspfRoles.extractRoles(channel));
        claimedRoles.retainAll(channelRoles);

        DelegatedTenantAuthenticationDetails tenantDetails = httpTenantDetails(
                channel,
                onBehalfTenant,
                Objects.requireNonNull(tenants, "tenants")
        );
        return authentication(username, claimedRoles, tenantDetails);
    }

    /**
     * Installs delegation from a trusted federation tunnel.
     *
     * <p>The WebSocket peer is authenticated before tunnel messages are accepted, so roles are
     * not intersected with a Spring channel principal. Empty roles remain valid for object member
     * ACL authorization. Tenant identifiers are still validated.</p>
     */
    public static Authentication installFromTrustedChannel(
            String onBehalfUser,
            Collection<String> onBehalfRoles,
            String onBehalfTenant
    ) {
        String username = requireTrustedUsername(onBehalfUser);
        LinkedHashSet<String> roles = normalizeRoles(onBehalfRoles);
        DelegatedTenantAuthenticationDetails tenantDetails = null;
        if (onBehalfTenant != null && !onBehalfTenant.isBlank()) {
            tenantDetails = new DelegatedTenantAuthenticationDetails(onBehalfTenant);
        }
        return authentication(username, roles, tenantDetails);
    }

    public static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        return normalized.isBlank() ? null : normalized;
    }

    public static List<String> parseRolesHeader(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (String role : header.split(",", -1)) {
            String normalized = normalizeRole(role);
            if (normalized != null) {
                roles.add(normalized);
            }
        }
        return List.copyOf(roles);
    }

    public static void applyHttpHeaders(HttpRequest.Builder builder, Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Objects.requireNonNull(builder, "builder")
                .setHeader(HEADER_ON_BEHALF_USER, snapshot.username())
                .setHeader(HEADER_ON_BEHALF_ROLES, String.join(",", snapshot.roles()));
        if (snapshot.tenantId() != null && !snapshot.tenantId().isBlank()) {
            builder.setHeader(HEADER_ON_BEHALF_TENANT, snapshot.tenantId());
        }
    }

    private static boolean isNamedPrincipal(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null
                && !authentication.getName().isBlank();
    }

    private static LinkedHashSet<String> normalizeRoles(Collection<String> roles) {
        LinkedHashSet<String> normalizedRoles = new LinkedHashSet<>();
        if (roles == null) {
            return normalizedRoles;
        }
        for (String role : roles) {
            String normalized = normalizeRole(role);
            if (normalized != null) {
                normalizedRoles.add(normalized);
            }
        }
        return normalizedRoles;
    }

    private static String requireDelegatedUsername(String username) {
        if (username == null || username.isBlank()) {
            throw forbidden("On-behalf-of user is required");
        }
        return username.trim();
    }

    private static String requireTrustedUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("On-behalf-of user is required");
        }
        return username.trim();
    }

    private static DelegatedTenantAuthenticationDetails httpTenantDetails(
            Authentication channel,
            String onBehalfTenant,
            TenantScopeService tenants
    ) {
        if (onBehalfTenant == null) {
            return null;
        }
        if (onBehalfTenant.isBlank()) {
            throw forbidden("Invalid on-behalf-of tenant");
        }

        DelegatedTenantAuthenticationDetails details;
        try {
            details = new DelegatedTenantAuthenticationDetails(onBehalfTenant);
        } catch (IllegalArgumentException error) {
            throw forbidden("Invalid on-behalf-of tenant");
        }

        if (!IspfRoles.isGlobalAdmin(channel)) {
            String channelTenant = tenants.resolveTenantId(channel)
                    .orElseThrow(() -> forbidden("Federation channel has no tenant scope"));
            if (!details.tenantId().equals(channelTenant)) {
                throw forbidden("On-behalf-of tenant is outside the channel tenant scope");
            }
        }
        return details;
    }

    private static Authentication authentication(
            String username,
            Collection<String> roles,
            DelegatedTenantAuthenticationDetails tenantDetails
    ) {
        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(username, "N/A", authorities);
        if (tenantDetails != null) {
            authentication.setDetails(tenantDetails);
        }
        return authentication;
    }

    private static ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }
}
