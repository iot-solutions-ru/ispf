package com.ispf.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Logs production-hardening warnings when unsafe defaults are active outside local/test.
 * Default users (admin/admin) in local/dev are intentional and are not flagged.
 */
@Component
public class StartupSecurityGuard {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityGuard.class);
    private static final Set<String> RELAXED_PROFILES = Set.of("local", "test");

    private final Environment environment;
    private final CommercialLicenseProperties licenseProperties;
    private final WebSocketProperties webSocketProperties;
    private final IspfSecurityProperties securityProperties;

    public StartupSecurityGuard(
            Environment environment,
            CommercialLicenseProperties licenseProperties,
            WebSocketProperties webSocketProperties,
            IspfSecurityProperties securityProperties
    ) {
        this.environment = environment;
        this.licenseProperties = licenseProperties;
        this.webSocketProperties = webSocketProperties;
        this.securityProperties = securityProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnOnUnsafeDefaults() {
        if (isRelaxedProfile()) {
            return;
        }
        List<String> warnings = new ArrayList<>();
        if (!licenseProperties.isEnforce()) {
            warnings.add("ispf.license.enforce=false (unsigned/open analytics & driver packs may load)");
        }
        if (!licenseProperties.isRequireSignedBundles()) {
            warnings.add("ispf.license.require-signed-bundles=false (unsigned bundles can deploy)");
        }
        String origins = webSocketProperties.getAllowedOriginPatterns();
        if (origins == null || origins.isBlank() || "*".equals(origins.trim())) {
            warnings.add("ispf.websocket.allowed-origin-patterns=* (set ISPF_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS in prod)");
        }
        if (securityProperties.isLocalRoleHeaderEnabled()) {
            warnings.add("ispf.security.local-role-header-enabled=true (must stay false outside local/test)");
        }
        if (!securityProperties.isRbacEnabled()) {
            warnings.add("ispf.security.rbac-enabled=false (API open)");
        }
        if (warnings.isEmpty()) {
            log.info("Startup security guard: license enforce, signed bundles, and WS origins look hardened");
            return;
        }
        log.warn(
                "Startup security guard (profiles={}): {}",
                Arrays.toString(environment.getActiveProfiles()),
                String.join("; ", warnings)
        );
    }

    private boolean isRelaxedProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile == null ? "" : profile.toLowerCase(Locale.ROOT))
                .anyMatch(RELAXED_PROFILES::contains);
    }
}
