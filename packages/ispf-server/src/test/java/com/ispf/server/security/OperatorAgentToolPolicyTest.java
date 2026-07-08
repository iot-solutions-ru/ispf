package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorAgentToolPolicyTest {

    private final OperatorAgentToolPolicy policy = new OperatorAgentToolPolicy();

    @Test
    void mesSupervisorRoleGetsFullOperatorToolSet() {
        var auth = new UsernamePasswordAuthenticationToken(
                "supervisor",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + PlatformRoleService.MES_SUPERVISOR))
        );
        Set<String> tools = policy.allowedTools(auth);
        assertTrue(tools.contains("run_report"));
        assertTrue(tools.contains("list_work_queue"));
    }

    @Test
    void operatorReadonlyRoleGetsRestrictedToolSet() {
        var auth = new UsernamePasswordAuthenticationToken(
                "viewer",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + PlatformRoleService.OPERATOR_READONLY))
        );
        Set<String> tools = policy.allowedTools(auth);
        assertTrue(tools.contains("list_variables"));
        assertFalse(tools.contains("run_report"));
        assertFalse(tools.contains("list_work_queue"));
    }

    @Test
    void builtInOperatorRoleKeepsFullAllowlist() {
        var auth = new UsernamePasswordAuthenticationToken(
                "operator",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_operator"))
        );
        Set<String> tools = policy.allowedTools(auth);
        assertTrue(tools.contains("run_report"));
        assertTrue(tools.contains("remember_app_memory"));
    }
}
