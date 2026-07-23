package com.ispf.server.websocket;

import com.ispf.server.config.IspfSecurityProperties;
import com.ispf.server.security.PlatformUserService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Authenticates platform session tokens (issued by {@code /api/v1/auth/login}) on WebSocket
 * handshakes. Registered only in local/test profiles, mirroring {@code LocalSecurityConfig}:
 * the prod (keycloak) HTTP chain rejects platform tokens, so WebSocket must not silently
 * widen the attack surface by accepting them there.
 */
@Component
@Profile({"local", "test"})
public class PlatformTokenAuthenticator {

    private final PlatformUserService userService;
    private final IspfSecurityProperties securityProperties;

    public PlatformTokenAuthenticator(
            PlatformUserService userService,
            IspfSecurityProperties securityProperties
    ) {
        this.userService = userService;
        this.securityProperties = securityProperties;
    }

    public Optional<PlatformUserService.AuthenticatedUser> authenticate(String token) {
        if (!securityProperties.isTokenAuthEnabled()) {
            return Optional.empty();
        }
        return userService.authenticateToken(token);
    }
}
