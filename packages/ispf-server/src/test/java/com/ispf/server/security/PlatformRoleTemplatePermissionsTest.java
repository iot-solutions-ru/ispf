package com.ispf.server.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformRoleTemplatePermissionsTest {

    @Test
    void mesSupervisorIncludesReportAndWorkQueueTools() {
        Set<String> tools = PlatformRoleTemplatePermissions.operatorAgentTools(PlatformRoleService.MES_SUPERVISOR)
                .orElseThrow();
        assertTrue(tools.contains("run_report"));
        assertTrue(tools.contains("list_work_queue"));
        assertTrue(tools.contains("invoke_bff"));
        assertTrue(tools.contains("remember_app_memory"));
    }

    @Test
    void operatorReadonlyExcludesMutatingAndSupervisorTools() {
        Set<String> tools = PlatformRoleTemplatePermissions.operatorAgentTools(PlatformRoleService.OPERATOR_READONLY)
                .orElseThrow();
        assertTrue(tools.contains("list_variables"));
        assertTrue(tools.contains("get_variable_history"));
        assertFalse(tools.contains("run_report"));
        assertFalse(tools.contains("list_work_queue"));
        assertFalse(tools.contains("invoke_bff"));
        assertFalse(tools.contains("remember_app_memory"));
    }

    @Test
    void mesSupervisorScopePrefixesCoverMesTree() {
        var prefixes = PlatformRoleTemplatePermissions.scopePathPrefixes(PlatformRoleService.MES_SUPERVISOR)
                .orElseThrow();
        assertTrue(prefixes.contains("root.platform.mes"));
        assertTrue(prefixes.contains("root.platform.workflows"));
    }
}
