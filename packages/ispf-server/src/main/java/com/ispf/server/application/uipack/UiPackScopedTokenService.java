package com.ispf.server.application.uipack;

import com.ispf.server.config.IspfRoles;
import com.ispf.server.config.IspfSecurityProperties;
import com.ispf.server.security.PlatformAuthSessionStore;
import com.ispf.server.security.PlatformUserStore;
import com.ispf.server.security.UiPackScopedAuthority;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ADR-0073: mints a platform bearer whose session row carries {@code uiPackAppId}.
 * The token is BFF-scoped even when the named user is an admin.
 * OIDC deployments do not use this mint; they put the same claim on the access token.
 */
@Service
public class UiPackScopedTokenService {

    private static final int DEFAULT_TTL_HOURS = 12;
    private static final int MAX_TTL_HOURS = 168;

    private final PlatformAuthSessionStore sessionStore;
    private final PlatformUserStore userStore;
    private final IspfSecurityProperties securityProperties;
    private final Environment environment;

    public UiPackScopedTokenService(
            PlatformAuthSessionStore sessionStore,
            PlatformUserStore userStore,
            IspfSecurityProperties securityProperties,
            Environment environment
    ) {
        this.sessionStore = sessionStore;
        this.userStore = userStore;
        this.securityProperties = securityProperties;
        this.environment = environment;
    }

    @Transactional
    public Map<String, Object> mint(String username, String appId, Integer ttlHours, Authentication caller) {
        if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Scoped ui-pack tokens are minted only when platform token auth is active (local/test). "
                            + "On OIDC, put custom claim uiPackAppId on the access token."
            );
        }
        if (!securityProperties.isTokenAuthEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform token auth is disabled");
        }
        if (!IspfRoles.isGlobalAdmin(caller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required to mint a ui-pack token");
        }
        String normalizedApp = appId == null ? "" : appId.trim();
        if (!UiPackSupport.isSafeAppId(normalizedApp)) {
            throw new IllegalArgumentException("Invalid ui-pack appId");
        }
        String normalizedUser = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        PlatformUserStore.PlatformUser user = userStore.findByUsername(normalizedUser)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + normalizedUser));
        if (!user.enabled()) {
            throw new IllegalArgumentException("User is disabled");
        }
        int hours = ttlHours != null && ttlHours > 0 ? Math.min(ttlHours, MAX_TTL_HOURS) : DEFAULT_TTL_HOURS;
        sessionStore.deleteExpired(Instant.now());
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plusSeconds(hours * 3600L);
        sessionStore.save(token, user.username(), expiresAt, normalizedApp);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("expiresAt", expiresAt.toString());
        response.put("username", user.username());
        response.put(UiPackScopedAuthority.CLAIM, normalizedApp);
        response.put("purpose", "ui-pack-bff");
        response.put("ttlHours", hours);
        return response;
    }
}
