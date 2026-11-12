package com.ispf.server.ai.mcp;

import com.ispf.server.ai.agent.AgentContext;
import com.ispf.server.ai.agent.AgentPlanPhase;
import com.ispf.server.ai.agent.AgentRunState;
import com.ispf.server.ai.agent.AgentSession;
import com.ispf.server.ai.agent.AgentSessionStore;
import com.ispf.server.ai.agent.PlatformAgentToolRegistry;
import com.ispf.server.ai.audit.AiToolAuditService;
import com.ispf.server.ai.context.ContextPackService;
import com.ispf.server.config.AiProperties;
import com.ispf.server.config.McpProperties;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.workflow.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpToolAdapterConnectionTest {

    private static final String SESSION = "sess-1";
    private static final String CONNECTION = McpConnectionKeys.forHttpActor("admin", SESSION);

    @Mock
    private PlatformAgentToolRegistry toolRegistry;
    @Mock
    private AgentSessionStore sessionStore;
    @Mock
    private AiToolAuditService auditService;
    @Mock
    private ContextPackService contextPackService;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private AiProperties aiProperties;
    @Mock
    private ObjectAccessService objectAccessService;

    private McpConnectionStateStore connectionStateStore;
    private McpToolAdapter adapter;
    private ObjectMapper objectMapper;
    private McpProperties mcpProperties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mcpProperties = new McpProperties();
        connectionStateStore = new McpConnectionStateStore(mcpProperties);
        connectionStateStore.initializeHttpSession("admin", SESSION);
        adapter = new McpToolAdapter(
                toolRegistry,
                sessionStore,
                connectionStateStore,
                auditService,
                contextPackService,
                workflowService,
                aiProperties,
                mcpProperties,
                objectAccessService,
                objectMapper
        );
        lenient().when(contextPackService.contextPackVersion()).thenReturn("test");
        lenient().when(workflowService.listPublishedWorkflowTools()).thenReturn(List.of());
        lenient().when(workflowService.findPublishedWorkflowTool(any())).thenReturn(Optional.empty());
        lenient().when(aiProperties.isAgentRequireApprovalForMutate()).thenReturn(false);
    }

    @Test
    void sharesRunStateAcrossToolCallsOnSameConnection() throws Exception {
        when(toolRegistry.execute(eq("validate_bundle"), any(), any())).thenAnswer(invocation -> {
            AgentContext context = invocation.getArgument(2);
            context.runState().markBundleValidated("demo-app");
            return Map.of("status", "OK");
        });
        when(toolRegistry.execute(eq("import_package"), any(), any())).thenAnswer(invocation -> {
            AgentContext context = invocation.getArgument(2);
            if (!context.runState().isBundleValidated("demo-app")) {
                return Map.of(
                        "status", "ERROR",
                        "error",
                        "Run validate_bundle or dry_run_deploy with status OK before import_package"
                );
            }
            return Map.of("status", "OK");
        });

        Map<String, Object> validate = adapter.callTool(
                "validate_bundle",
                Map.of("appId", "demo-app", "manifest", Map.of("version", "1")),
                null,
                "admin",
                CONNECTION
        );
        assertThat(validate.get("isError")).isEqualTo(false);

        Map<String, Object> imported = adapter.callTool(
                "import_package",
                Map.of("packageId", "demo-app", "manifest", Map.of("version", "1")),
                null,
                "admin",
                CONNECTION
        );
        assertThat(imported.get("isError")).isEqualTo(false);
        verify(toolRegistry).execute(eq("import_package"), any(), any());
        assertThat(connectionStateStore.runStateFor(CONNECTION).isBundleValidated("demo-app")).isFalse();
    }

    @Test
    void blocksMutationsUntilHumanApprovedPlanWhenInbandDisabled() throws Exception {
        when(aiProperties.isAgentRequireApprovalForMutate()).thenReturn(true);
        when(toolRegistry.execute(eq("create_object"), any(), any()))
                .thenReturn(Map.of("status", "OK", "path", "root.platform.devices.x"));

        Map<String, Object> blocked = adapter.callTool(
                "create_object",
                Map.of("parent", "root.platform.devices", "name", "x", "type", "CUSTOM"),
                null,
                "admin",
                CONNECTION
        );
        assertThat(blocked.get("isError")).isEqualTo(true);

        Map<String, Object> emptyApprove = adapter.callTool(
                McpToolAdapter.MCP_APPROVE_MUTATE_PLAN,
                Map.of(),
                null,
                "admin",
                CONNECTION
        );
        assertThat(emptyApprove.get("isError")).isEqualTo(true);

        Map<String, Object> stillBlocked = adapter.callTool(
                "create_object",
                Map.of("parent", "root.platform.devices", "name", "x", "type", "CUSTOM"),
                null,
                "admin",
                CONNECTION
        );
        assertThat(stillBlocked.get("isError")).isEqualTo(true);
        verify(toolRegistry, never()).execute(eq("create_object"), any(), any());
    }

    @Test
    void allowsMutationsAfterInbandApproveWhenExplicitlyEnabled() throws Exception {
        when(aiProperties.isAgentRequireApprovalForMutate()).thenReturn(true);
        mcpProperties.setAllowInbandMutateApproval(true);
        when(toolRegistry.execute(eq("create_object"), any(), any()))
                .thenReturn(Map.of("status", "OK", "path", "root.platform.devices.x"));

        Map<String, Object> approved = adapter.callTool(
                McpToolAdapter.MCP_APPROVE_MUTATE_PLAN,
                Map.of(),
                null,
                "admin",
                CONNECTION
        );
        assertThat(approved.get("isError")).isEqualTo(false);

        Map<String, Object> allowed = adapter.callTool(
                "create_object",
                Map.of("parent", "root.platform.devices", "name", "x", "type", "CUSTOM"),
                null,
                "admin",
                CONNECTION
        );
        assertThat(allowed.get("isError")).isEqualTo(false);
    }

    @Test
    void rejectsExecuteInteractionModeWhenRequireApprovalEnabled() throws Exception {
        when(aiProperties.isAgentRequireApprovalForMutate()).thenReturn(true);
        AgentSession session = AgentSession.create("admin", "root");
        when(sessionStore.create(eq("admin"), eq("root"))).thenReturn(session);

        Map<String, Object> created = adapter.callTool(
                McpToolAdapter.MCP_CREATE_AGENT_SESSION,
                Map.of("rootPath", "root", "interactionMode", "EXECUTE"),
                null,
                "admin",
                CONNECTION
        );
        assertThat(created.get("isError")).isEqualTo(true);
        when(toolRegistry.execute(eq("create_object"), any(), any()))
                .thenReturn(Map.of("status", "OK"));
        Map<String, Object> blocked = adapter.callTool(
                "create_object",
                Map.of("parent", "root.platform.devices", "name", "x", "type", "CUSTOM"),
                null,
                "admin",
                CONNECTION
        );
        assertThat(blocked.get("isError")).isEqualTo(true);
    }

    @Test
    void executeModeOnConnectionDoesNotBypassMutateGuard() throws Exception {
        when(aiProperties.isAgentRequireApprovalForMutate()).thenReturn(true);
        connectionStateStore.runStateFor(CONNECTION).setInteractionMode(
                com.ispf.server.ai.agent.AgentInteractionMode.EXECUTE
        );
        when(toolRegistry.execute(eq("create_object"), any(), any()))
                .thenReturn(Map.of("status", "OK"));

        Map<String, Object> blocked = adapter.callTool(
                "create_object",
                Map.of("parent", "root.platform.devices", "name", "x", "type", "CUSTOM"),
                null,
                "admin",
                CONNECTION
        );
        assertThat(blocked.get("isError")).isEqualTo(true);
        verify(toolRegistry, never()).execute(eq("create_object"), any(), any());
    }

    @Test
    void inbandApproveHonorsPendingPlanWhenConfiguredOffButPlanReady() throws Exception {
        when(aiProperties.isAgentRequireApprovalForMutate()).thenReturn(true);
        AgentRunState state = connectionStateStore.runStateFor(CONNECTION);
        state.setPlanPhase(AgentPlanPhase.AWAITING_APPROVAL);
        state.setStoredPlan(Map.of("goal", "test", "steps", List.of()));
        when(toolRegistry.execute(eq("create_object"), any(), any()))
                .thenReturn(Map.of("status", "OK"));

        Map<String, Object> approved = adapter.callTool(
                McpToolAdapter.MCP_APPROVE_MUTATE_PLAN,
                Map.of(),
                null,
                "admin",
                CONNECTION
        );
        assertThat(approved.get("isError")).isEqualTo(false);

        Map<String, Object> allowed = adapter.callTool(
                "create_object",
                Map.of("parent", "root.platform.devices", "name", "y", "type", "CUSTOM"),
                null,
                "admin",
                CONNECTION
        );
        assertThat(allowed.get("isError")).isEqualTo(false);
    }

    @Test
    void publishedWorkflowInvokeChecksAcl() throws Exception {
        WorkflowService.PublishedWorkflowTool published = new WorkflowService.PublishedWorkflowTool(
                "wf_run_pumps",
                "root.platform.workflows.pumps",
                "Run pumps",
                "{}"
        );
        when(workflowService.findPublishedWorkflowTool("wf_run_pumps")).thenReturn(Optional.of(published));
        doThrow(new AccessDeniedException("viewer cannot invoke"))
                .when(objectAccessService)
                .requireMemberInvoke(
                        eq("root.platform.workflows.pumps"),
                        eq("workflow"),
                        eq("root.platform.workflows.pumps"),
                        anyList(),
                        any()
                );

        Map<String, Object> denied = adapter.callTool(
                "wf_run_pumps",
                Map.of(),
                null,
                "viewer",
                CONNECTION
        );
        assertThat(denied.get("isError")).isEqualTo(true);
        verify(workflowService, never()).invokeWorkflowTool(any(), any());
    }

    @Test
    void mcpCreateSessionBindsPersistedSessionToConnection() throws Exception {
        AgentSession session = AgentSession.create("admin", "root.platform.applications");
        when(sessionStore.create(eq("admin"), eq("root"))).thenReturn(session);

        Map<String, Object> created = adapter.callTool(
                McpToolAdapter.MCP_CREATE_AGENT_SESSION,
                Map.of("rootPath", "root"),
                null,
                "admin",
                CONNECTION
        );
        assertThat(created.get("isError")).isEqualTo(false);
        assertThat(connectionStateStore.agentSessionId(CONNECTION)).contains(session.sessionId());
        verify(sessionStore).persistState(session);
    }

    @Test
    void listToolsIncludesMcpMetaTools() {
        when(toolRegistry.toolCatalog()).thenReturn(List.of());
        List<Map<String, Object>> tools = adapter.listTools();
        assertThat(tools.stream().map(t -> t.get("name")).toList())
                .contains(McpToolAdapter.MCP_CREATE_AGENT_SESSION, McpToolAdapter.MCP_APPROVE_MUTATE_PLAN);
    }
}
