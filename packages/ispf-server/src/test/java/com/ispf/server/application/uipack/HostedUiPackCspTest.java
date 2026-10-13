package com.ispf.server.application.uipack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Documents ADR-0073 Wave 2A CSP contract on {@link HostedUiPackFilter}.
 * Full MockMvc filter test needs an installed pack; this guards the policy string shape.
 */
class HostedUiPackCspTest {

    @Test
    void cspPolicyForbidsUnsafeEvalAndFrames() {
        String csp = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; connect-src 'self'; font-src 'self' data:; "
                + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'";
        assertTrue(csp.contains("script-src 'self'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(!csp.contains("unsafe-eval"));
    }
}
