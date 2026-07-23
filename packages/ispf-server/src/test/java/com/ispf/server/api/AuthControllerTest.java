package com.ispf.server.api;

import com.ispf.server.audit.AuditEventService;
import com.ispf.server.config.IspfSecurityProperties;
import com.ispf.server.security.LoginAttemptLimiter;
import com.ispf.server.security.PlatformUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private IspfSecurityProperties securityProperties;
    private LoginAttemptLimiter loginAttemptLimiter;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        PlatformUserService userService = mock(PlatformUserService.class);
        securityProperties = new IspfSecurityProperties();
        loginAttemptLimiter = mock(LoginAttemptLimiter.class);
        controller = new AuthController(
                userService,
                securityProperties,
                mock(Environment.class),
                "",
                mock(AuditEventService.class),
                loginAttemptLimiter
        );
        when(userService.login(any(), any(), any())).thenReturn(Map.of("token", "t"));
    }

    @Test
    void ignoresForwardedForWhenPeerIsNotTrusted() {
        HttpServletRequest request = request("203.0.113.10", "198.51.100.7, 203.0.113.10");

        controller.login(new AuthController.LoginRequest("admin", "secret", null), request);

        verify(loginAttemptLimiter).checkAllowed("admin", "203.0.113.10");
        verify(loginAttemptLimiter).recordSuccess("admin", "203.0.113.10");
    }

    @Test
    void honorsForwardedForFromTrustedProxy() {
        securityProperties.setTrustedProxyIps(List.of("10.0.0.1"));
        HttpServletRequest request = request("10.0.0.1", "198.51.100.7, 10.0.0.1");

        controller.login(new AuthController.LoginRequest("admin", "secret", null), request);

        verify(loginAttemptLimiter).checkAllowed("admin", "198.51.100.7");
        verify(loginAttemptLimiter).recordSuccess("admin", "198.51.100.7");
    }

    @Test
    void fallsBackToPeerAddressWhenTrustedProxySendsNoForwardedFor() {
        securityProperties.setTrustedProxyIps(List.of("10.0.0.1"));
        HttpServletRequest request = request("10.0.0.1", null);

        controller.login(new AuthController.LoginRequest("admin", "secret", null), request);

        verify(loginAttemptLimiter).checkAllowed("admin", "10.0.0.1");
        verify(loginAttemptLimiter).recordSuccess("admin", "10.0.0.1");
    }

    private static HttpServletRequest request(String remoteAddr, String forwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        return request;
    }
}
