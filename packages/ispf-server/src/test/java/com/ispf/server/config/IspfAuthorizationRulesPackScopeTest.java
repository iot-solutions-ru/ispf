package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IspfAuthorizationRulesPackScopeTest {

    @Test
    void operatorAgentPathParsesAppId() {
        assertEquals(
                "oil-control",
                IspfAuthorizationRules.operatorAgentAppId("/api/v1/operator-apps/oil-control/agent/status")
        );
        assertNull(IspfAuthorizationRules.operatorAgentAppId("/api/v1/operator-apps/oil-control/ui"));
        assertNull(IspfAuthorizationRules.operatorAgentAppId("/api/v1/security/users"));
    }

    @Test
    void scopedTokenMayCallItsOwnOperatorAgent() {
        MockHttpServletRequest request = agentRequest("partner-app", "http://localhost/apps/partner-app/");
        assertFalse(IspfAuthorizationRules.deniesPackScopedApi(request, "partner-app"));
    }

    @Test
    void scopedTokenCannotCallAnotherAppsAgent() {
        MockHttpServletRequest request = agentRequest("oil-control", "http://localhost/apps/partner-app/");
        assertTrue(IspfAuthorizationRules.deniesPackScopedApi(request, "partner-app"));
    }

    @Test
    void mismatchedPackRefererDeniesAgent() {
        MockHttpServletRequest request = agentRequest("partner-app", "http://localhost/apps/other-app/");
        assertTrue(IspfAuthorizationRules.deniesPackScopedApi(request, "partner-app"));
    }

    @Test
    void adminApisStayDenied() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/security/users");
        request.setRequestURI("/api/v1/security/users");
        assertTrue(IspfAuthorizationRules.deniesPackScopedApi(request, "partner-app"));
    }

    private static MockHttpServletRequest agentRequest(String appId, String referer) {
        String path = "/api/v1/operator-apps/" + appId + "/agent/status";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        request.addHeader("Referer", referer);
        return request;
    }
}
