package com.ispf.server.ai.agent;

import com.ispf.server.operator.OperatorAgentMemoryService;
import com.ispf.server.operator.OperatorAppUiService;
import com.ispf.server.security.PlatformRoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BL-179 GA: operator agent continuity without LLM —
 * memory across turns, ru/en prompt headers, scoped tools, role allowlists.
 */
@SpringBootTest
@ActiveProfiles("test")
class OperatorAgentContinuityIntegrationTest {

    private static final String APP_ID = "platform-primitive";

    @Autowired
    private PlatformAgentToolRegistry toolRegistry;

    @Autowired
    private OperatorAgentMemoryService memoryService;

    @Autowired
    private OperatorAppUiService operatorAppUiService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void memoryLocaleScopeAndRoleContinuity() throws Exception {
        ensureOperatorApp();

        OperatorAgentScope scope = new OperatorAgentScope(
                APP_ID,
                "Platform Primitive",
                List.of(
                        "root.platform.devices.platform-primitive-hub",
                        "root.platform.dashboards.platform-primitive-overview",
                        "root.platform.applications." + APP_ID,
                        "root.platform.operator-apps." + APP_ID
                ),
                "root.platform.devices.platform-primitive-hub"
        );

        var supervisorAuth = new UsernamePasswordAuthenticationToken(
                "supervisor",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + PlatformRoleService.MES_SUPERVISOR))
        );

        // Turn 1 — remember a plant fact
        AgentContext turn1 = new AgentContext(
                "supervisor",
                supervisorAuth,
                new AgentRunState(),
                AgentProfile.OPERATOR,
                scope
        );
        Map<String, Object> remembered = toolRegistry.execute(
                "remember_app_memory",
                Map.of(
                        "kind", "glossary",
                        "topic", "hub-status",
                        "content", "Hub status > 90 means overload on line A"
                ),
                turn1
        );
        assertEquals("OK", remembered.get("status"), () -> String.valueOf(remembered));

        // Turn 2 — new context/session, same app: memory must still be listed
        AgentContext turn2 = new AgentContext(
                "supervisor",
                supervisorAuth,
                new AgentRunState(),
                AgentProfile.OPERATOR,
                scope
        );
        Map<String, Object> listed = toolRegistry.execute(
                "list_app_memory",
                Map.of("query", "overload"),
                turn2
        );
        assertEquals("OK", listed.get("status"), () -> String.valueOf(listed));
        assertTrue(((Number) listed.get("count")).intValue() >= 1, () -> String.valueOf(listed));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> memories = (List<Map<String, Object>>) listed.get("memories");
        assertTrue(
                memories.stream().anyMatch(row -> String.valueOf(row.get("content")).contains("overload")),
                () -> String.valueOf(memories)
        );

        // Locale: Cyrillic user message → Russian memory header in prompt injection
        String ruSection = memoryService.formatPromptSection(
                APP_ID,
                "Что значит перегрузка на линии?",
                Locale.forLanguageTag("ru")
        );
        assertTrue(ruSection.contains("Память приложения"), ruSection);
        assertTrue(ruSection.contains("overload") || ruSection.contains("Hub status"), ruSection);

        String enSection = memoryService.formatPromptSection(
                APP_ID,
                "What does hub overload mean?",
                Locale.ENGLISH
        );
        assertTrue(enSection.contains("Application memory"), enSection);
        assertFalse(enSection.contains("Память приложения"), enSection);

        // Scope: path outside app prefixes is rejected in operator mode
        Map<String, Object> scoped = toolRegistry.execute(
                "list_variables",
                Map.of("path", "root.platform.devices.other-unrelated-device"),
                turn2
        );
        assertEquals("ERROR", scoped.get("status"), () -> String.valueOf(scoped));
        assertTrue(String.valueOf(scoped.get("error")).contains("outside operator app scope"),
                () -> String.valueOf(scoped));

        // Role: operator-readonly cannot use remember_app_memory
        var readonlyAuth = new UsernamePasswordAuthenticationToken(
                "viewer",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + PlatformRoleService.OPERATOR_READONLY))
        );
        AgentContext readonly = new AgentContext(
                "viewer",
                readonlyAuth,
                new AgentRunState(),
                AgentProfile.OPERATOR,
                scope
        );
        Map<String, Object> denied = toolRegistry.execute(
                "remember_app_memory",
                Map.of("content", "should not store"),
                readonly
        );
        assertEquals("ERROR", denied.get("status"), () -> String.valueOf(denied));
        assertTrue(String.valueOf(denied.get("error")).contains("not allowed"), () -> String.valueOf(denied));
    }

    @SuppressWarnings("unchecked")
    private void ensureOperatorApp() throws Exception {
        String bundleJson = new ClassPathResource("platform-primitive-bundle.json")
                .getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> manifest = objectMapper.readValue(bundleJson, Map.class);
        AgentContext admin = new AgentContext("admin", null, new AgentRunState());
        Map<String, Object> args = Map.of("appId", APP_ID, "manifest", manifest);
        assertEquals("OK", toolRegistry.execute("deploy_step_validate", args, admin).get("status"));
        assertEquals("OK", toolRegistry.execute("deploy_step_dry_run", args, admin).get("status"));
        assertEquals("OK", toolRegistry.execute("deploy_step_import", args, admin).get("status"));

        Map<String, Object> operatorUi = (Map<String, Object>) manifest.get("operatorUi");
        Map<String, Object> uiArgs = new LinkedHashMap<>();
        uiArgs.put("appId", operatorUi.get("appId"));
        uiArgs.put("title", operatorUi.get("title"));
        uiArgs.put("defaultDashboard", operatorUi.get("defaultDashboard"));
        uiArgs.put("dashboards", operatorUi.get("dashboards"));
        assertEquals("OK", toolRegistry.execute("deploy_step_operator_ui", uiArgs, admin).get("status"));

        Map<String, Object> ui = operatorAppUiService.getUi(APP_ID);
        assertEquals(APP_ID, ui.get("appId"));
    }
}
