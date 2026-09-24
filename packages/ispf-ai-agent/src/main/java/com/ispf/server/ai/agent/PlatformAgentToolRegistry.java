package com.ispf.server.ai.agent;

import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.ai.context.ContextPackSearchService;
import com.ispf.server.ai.tool.AiToolRegistry;
import com.ispf.server.platform.McpToolCatalogPort;
import com.ispf.server.security.OperatorAgentToolAllowlist;
import com.ispf.server.alert.AlertRuleService;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.script.PlatformScriptBridge;
import com.ispf.server.application.test.FunctionTestRunner;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.bootstrap.LabBlueprintBootstrap;
import com.ispf.server.application.catalog.ApplicationEventCatalogService;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.driver.DriverCatalog;
import com.ispf.server.event.EventJournalStore;
import com.ispf.server.event.EventService;
import com.ispf.server.federation.FederationBindService;
import com.ispf.server.function.FunctionInvokeAccessService;
import com.ispf.server.function.FunctionService;
import com.ispf.server.operator.OperatorAgentMemoryService;
import com.ispf.server.operator.OperatorAppDocumentService;
import com.ispf.server.operator.OperatorAppUiService;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.platform.HaystackExportService;
import com.ispf.server.report.ReportService;
import com.ispf.server.security.PlatformRoleService;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.OperatorAgentToolPolicy;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.mimic.MimicService;
import com.ispf.server.platform.analytics.AnalyticsAnalysisService;
import com.ispf.server.platform.analytics.AnalyticsCatalogRegistry;
import com.ispf.server.workflow.WorkflowAiActionService;
import com.ispf.server.application.binding.ApplicationSqlBindingService;
import com.ispf.server.application.bundle.ApplicationBundlePullFromTreeService;
import com.ispf.server.application.data.ApplicationDataService;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.platform.time.PlatformTimeZoneResolver;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import com.ispf.server.schedule.ScheduleObjectService;
import com.ispf.server.workflow.WorkQueueService;
import com.ispf.server.workflow.WorkflowInstanceCancelService;
import com.ispf.server.workflow.WorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PlatformAgentToolRegistry implements McpToolCatalogPort {

    static final Set<String> OPERATOR_TOOLS = OperatorAgentToolAllowlist.ALLOWED_TOOLS;

    private final Map<String, PlatformAgentTool> toolsByName;
    private final OperatorAgentToolPolicy operatorAgentToolPolicy;
    private final WorkflowService workflowService;

    public PlatformAgentToolRegistry(
            ContextPackSearchService contextPackSearchService,
            DriverCatalog driverCatalog,
            ApplicationDataStore applicationDataStore,
            ApplicationBundleSnapshotStore bundleSnapshotStore,
            FunctionService functionService,
            FunctionInvokeAccessService functionInvokeAccessService,
            ApplicationFunctionStore applicationFunctionStore,
            ApplicationEventCatalogService eventCatalogService,
            EventService eventService,
            BlueprintRegistry BlueprintRegistry,
            BlueprintApplicationService BlueprintApplicationService,
            LabBlueprintBootstrap LabBlueprintBootstrap,
            ObjectTreePort ObjectTreePort,
            ObjectAccessService objectAccessService,
            VariableMemberAccessService variableMemberAccessService,
            TenantScopeService tenantScopeService,
            ObjectUiIconService objectUiIconService,
            ObjectTemplateService objectTemplateService,
            DashboardService dashboardService,
            ReportService reportService,
            WorkflowService workflowService,
            AutomationTreeService automationTreeService,
            DeviceProvisioningService deviceProvisioningService,
            FederationBindService federationBindService,
            DriverRuntimeService driverRuntimeService,
            PlatformUserService platformUserService,
            PlatformRoleService platformRoleService,
            AiToolRegistry aiToolRegistry,
            ApplicationBundleDeployService bundleDeployService,
            OperatorAppUiService operatorAppUiService,
            BindingRulesService bindingRulesService,
            BindingDependencyIndex bindingDependencyIndex,
            BindingRuleEngine bindingRuleEngine,
            AgentRecipeCatalog agentRecipeCatalog,
            ObjectMapper objectMapper,
            VariableHistoryService variableHistoryService,
            WorkQueueService workQueueService,
            OperatorAgentMemoryService operatorAgentMemoryService,
            OperatorAppDocumentService operatorAppDocumentService,
            AgentSessionDocumentService agentSessionDocumentService,
            HaystackExportService haystackExportService,
            MimicService mimicService,
            WorkflowInstanceCancelService workflowInstanceCancelService,
            WorkflowInstanceRepository workflowInstanceRepository,
            ApplicationDataService applicationDataService,
            ApplicationSqlBindingService applicationSqlBindingService,
            ApplicationBundlePullFromTreeService applicationBundlePullFromTreeService,
            ScheduleObjectService scheduleObjectService,
            PlatformTimeZoneResolver platformTimeZoneResolver,
            OperatorAgentToolPolicy operatorAgentToolPolicy,
            AnalyticsCatalogRegistry analyticsCatalogRegistry,
            AnalyticsAnalysisService analyticsAnalysisService,
            WorkflowAiActionService workflowAiActionService,
            com.ispf.server.platform.analytics.engine.AnalyticsTagCatalogService analyticsTagCatalogService,
            com.ispf.server.platform.analytics.AnalyticsQueryService analyticsQueryService,
            com.ispf.server.platform.analytics.AnalyticsExpressionService analyticsExpressionService,
            com.ispf.server.expression.ExpressionFormalVerificationService formalVerificationService,
            FunctionTestRunner functionTestRunner,
            PlatformScriptBridge platformScriptBridge,
            AlertRuleService alertRuleService,
            EventJournalStore eventJournalStore
    ) {
        this.operatorAgentToolPolicy = operatorAgentToolPolicy;
        this.workflowService = workflowService;
        List<PlatformAgentTool> tools = new ArrayList<>();
        tools.addAll(AgentKnowledgeTools.all(
                contextPackSearchService,
                driverCatalog,
                applicationDataStore,
                bundleSnapshotStore
        ));
        tools.add(AgentSessionKnowledgeTools.searchSessionContextTool(agentSessionDocumentService));
        tools.addAll(AgentReportTools.all(
                reportService,
                ObjectTreePort,
                objectAccessService,
                tenantScopeService
        ));
        tools.addAll(AgentMimicTools.all(
                mimicService,
                ObjectTreePort,
                objectAccessService,
                tenantScopeService,
                objectMapper
        ));
        tools.addAll(AgentWorkflowTools.all(
                workflowService,
                workflowInstanceCancelService,
                workflowInstanceRepository,
                ObjectTreePort,
                objectAccessService,
                tenantScopeService,
                objectMapper
        ));
        tools.addAll(AgentAnalyticsTools.all(
                analyticsCatalogRegistry,
                analyticsAnalysisService,
                variableHistoryService,
                workflowAiActionService,
                objectAccessService,
                variableMemberAccessService,
                tenantScopeService,
                analyticsTagCatalogService,
                analyticsQueryService,
                analyticsExpressionService
        ));
        tools.addAll(AgentApplicationTools.all(
                applicationDataService,
                applicationSqlBindingService,
                bundleDeployService,
                applicationBundlePullFromTreeService,
                applicationFunctionStore,
                objectMapper,
                objectAccessService,
                tenantScopeService
        ));
        tools.addAll(AgentPlatformTools.all(
                scheduleObjectService,
                bindingRulesService,
                bindingDependencyIndex,
                bindingRuleEngine,
                platformTimeZoneResolver,
                haystackExportService,
                ObjectTreePort,
                objectAccessService,
                tenantScopeService,
                formalVerificationService
        ));
        tools.addAll(AgentFunctionTools.all(
                ObjectTreePort,
                objectAccessService,
                tenantScopeService,
                objectMapper
        ));
        tools.addAll(AgentVirtualDeviceTools.all(
                ObjectTreePort,
                objectAccessService,
                objectTemplateService,
                deviceProvisioningService,
                driverRuntimeService,
                LabBlueprintBootstrap,
                BlueprintRegistry,
                objectMapper
        ));
        tools.addAll(AgentBlueprintTools.all(
                BlueprintRegistry,
                BlueprintApplicationService,
                ObjectTreePort,
                objectAccessService,
                tenantScopeService
        ));
        tools.addAll(AgentDiscoveryTools.all(
                ObjectTreePort,
                objectAccessService,
                variableMemberAccessService,
                tenantScopeService,
                applicationFunctionStore,
                eventCatalogService,
                objectMapper
        ));
        tools.addAll(AgentActionTools.all(
                functionService,
                applicationFunctionStore,
                ObjectTreePort,
                objectAccessService,
                functionInvokeAccessService,
                tenantScopeService,
                eventService,
                BlueprintRegistry,
                haystackExportService,
                objectMapper
        ));
        tools.addAll(AgentTestTools.all(
                functionTestRunner,
                bundleDeployService,
                platformScriptBridge,
                alertRuleService,
                eventJournalStore,
                ObjectTreePort
        ));
        tools.addAll(AgentAutomationTools.all(
                automationTreeService,
                operatorAppUiService,
                ObjectTreePort,
                objectAccessService,
                tenantScopeService,
                bindingRulesService,
                bindingDependencyIndex,
                bindingRuleEngine,
                agentRecipeCatalog,
                objectMapper,
                formalVerificationService
        ));
        tools.addAll(AgentDeployPlaybookTools.all(
                objectMapper,
                aiToolRegistry,
                bundleDeployService,
                operatorAppUiService,
                contextPackSearchService
        ));
        tools.addAll(AgentTreeTools.all(
                ObjectTreePort,
                objectAccessService,
                variableMemberAccessService,
                tenantScopeService,
                objectUiIconService,
                objectMapper,
                driverRuntimeService,
                objectTemplateService,
                dashboardService,
                reportService,
                workflowService,
                automationTreeService,
                deviceProvisioningService,
                federationBindService,
                platformUserService,
                platformRoleService,
                aiToolRegistry,
                bundleDeployService
        ));
        tools.addAll(AgentOperatorTools.all(
                variableHistoryService,
                workQueueService,
                variableMemberAccessService,
                tenantScopeService,
                operatorAgentMemoryService,
                operatorAppDocumentService
        ));
        Map<String, PlatformAgentTool> index = new LinkedHashMap<>();
        for (PlatformAgentTool tool : tools) {
            index.put(tool.name(), tool);
        }
        for (PlatformAgentTool meta : AgentToolPackTools.all(() -> catalogRows(index))) {
            index.put(meta.name(), meta);
        }
        this.toolsByName = Map.copyOf(index);
    }

    private static List<Map<String, Object>> catalogRows(Map<String, PlatformAgentTool> index) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlatformAgentTool tool : index.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", tool.name());
            row.put("description", tool.description());
            row.put("inputSchema", tool.inputSchema());
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    @Override
    public int toolCount() {
        return toolCatalog().size() + workflowService.listPublishedWorkflowTools().size();
    }

    public List<Map<String, Object>> toolCatalog() {
        return toolCatalog(AgentProfile.ADMIN);
    }

    public List<Map<String, Object>> toolCatalog(AgentProfile profile) {
        return toolCatalog(profile, null);
    }

    public List<Map<String, Object>> toolCatalog(AgentProfile profile, Authentication authentication) {
        Set<String> operatorAllowed = profile == AgentProfile.OPERATOR
                ? operatorAgentToolPolicy.allowedTools(authentication)
                : Set.of();
        return toolsByName.values().stream()
                .filter(tool -> profile != AgentProfile.OPERATOR || operatorAllowed.contains(tool.name()))
                .map(tool -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", tool.name());
                    row.put("description", tool.description());
                    row.put("inputSchema", tool.inputSchema());
                    return row;
                })
                .toList();
    }

    public boolean isKnownTool(String toolName) {
        return toolName != null && toolsByName.containsKey(toolName);
    }

    public Map<String, Object> unknownToolResult(String toolName) {
        return AgentToolErrors.error(
                "UNKNOWN_TOOL",
                "Unknown tool: " + toolName,
                "",
                "Use exact snake_case tool names from the catalog. "
                        + "For workflows: create_object (type=WORKFLOW), save_workflow_bpmn, run_workflow. "
                        + "Never invent display names or Russian labels as tool names."
        );
    }

    public Map<String, Object> execute(String toolName, Map<String, Object> arguments, AgentContext context)
            throws Exception {
        if (context.isOperator()) {
            Set<String> allowed = operatorAgentToolPolicy.allowedTools(context.authentication());
            if (!allowed.contains(toolName)) {
                return Map.of(
                        "status", "ERROR",
                        "error", "Tool not allowed in operator mode: " + toolName
                );
            }
        }
        if (context.isOperator()) {
            try {
                AgentScopeGuard.enforceOperatorScope(toolName, arguments != null ? arguments : Map.of(), context.operatorScope());
            } catch (IllegalArgumentException ex) {
                return Map.of("status", "ERROR", "error", ex.getMessage());
            }
        }
        PlatformAgentTool tool = toolsByName.get(toolName);
        if (tool == null) {
            return unknownToolResult(toolName);
        }
        Map<String, Object> args = arguments != null ? arguments : Map.of();
        var schemaViolation = AgentToolSchemaValidator.validate(tool.inputSchema(), args);
        if (schemaViolation.isPresent()) {
            return schemaViolation.get().toErrorResult();
        }
        return tool.execute(args, context);
    }

}
