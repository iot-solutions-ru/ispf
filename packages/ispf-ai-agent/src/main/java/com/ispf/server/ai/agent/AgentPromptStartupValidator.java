package com.ispf.server.ai.agent;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fails fast at startup if agent playbooks or system prompt cannot be built.
 */
@Component
class AgentPromptStartupValidator {

    /** Unresolved String.format placeholder — must never survive into a rendered prompt. */
    private static final String PLACEHOLDER = "%s";

    private static final Logger log = LoggerFactory.getLogger(AgentPromptStartupValidator.class);

    private final PlatformAgentToolRegistry toolRegistry;

    AgentPromptStartupValidator(PlatformAgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @PostConstruct
    void validatePromptAssembly() {
        List<Map<String, Object>> catalog = toolRegistry.toolCatalog();
        for (Map<String, Object> row : catalog) {
            String toolName = String.valueOf(row.get("name"));
            if (!AgentToolInputSchemas.hasCatalogEntry(toolName)) {
                throw new IllegalStateException(
                        "Registered tool '" + toolName + "' lacks AgentToolInputSchemas catalog entry (ADR-0051)"
                );
            }
        }
        String prompt = AgentPromptBuilder.build("root", catalog, "");
        if (prompt.isBlank()) {
            throw new IllegalStateException("Agent system prompt is empty");
        }
        if (prompt.contains(PLACEHOLDER)) {
            throw new IllegalStateException("Agent system prompt contains unresolved " + PLACEHOLDER + " placeholder");
        }
        for (String playbook : List.of(
                AgentPlaybooks.snmpLocalhostMonitoring(),
                AgentPlaybooks.dashboardLayoutEditing(),
                AgentPlaybooks.snmpIfMibExtension(),
                AgentPlaybooks.virtualClusterMonitoring(),
                AgentPlaybooks.scadaMimicGuide(),
                AgentPlaybooks.platformObjectTypesGuide()
        )) {
            if (playbook.contains(PLACEHOLDER)) {
                throw new IllegalStateException("Agent playbook contains forbidden " + PLACEHOLDER + " placeholder");
            }
        }
        log.info("Agent prompt validated ({} tools, {} chars)", catalog.size(), prompt.length());
        String askPrompt = AgentAskPromptBuilder.build("root", catalog, "", false);
        if (askPrompt.isBlank() || askPrompt.contains("PLAN-BEFORE-EXECUTE")) {
            throw new IllegalStateException("Ask mode prompt must not contain planning instructions");
        }
        log.info("Ask mode prompt validated ({} read-only tools, {} chars)",
                AgentAskPromptBuilder.readOnlyToolCatalog(catalog).size(), askPrompt.length());
    }
}
