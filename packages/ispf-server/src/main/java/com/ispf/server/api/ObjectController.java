package com.ispf.server.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.server.federation.FederationBindService;
import com.ispf.server.federation.FederationProxyService;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import com.ispf.server.plugin.blueprint.SystemObjectStructureService;
import com.ispf.server.plugin.blueprint.dto.AppliedBlueprintDto;
import com.ispf.server.api.dto.ObjectEditorDto;
import com.ispf.server.object.ObjectChangeEvent;
import com.ispf.server.object.ObjectRevisionConflictException;
import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.api.support.ObjectCollaborationSupport;
import com.ispf.server.object.ObjectBulkDeleteService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectSearchService;
import com.ispf.server.object.ObjectTreeDriverEnricher;
import com.ispf.server.object.VisualGroupService;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.report.ReportService;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.audit.AuditEventService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.security.PlatformRoleService;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import com.ispf.server.tenant.TenantQuotaService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantPaths;
import com.ispf.server.tenant.TenantVirtualRootService;
import com.ispf.server.workflow.WorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/objects")
public class ObjectController {

    private static final Logger log = LoggerFactory.getLogger(ObjectController.class);

    private final ObjectManager objectManager;
    private final ObjectTemplateService objectTemplateService;
    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final WorkflowService workflowService;
    private final PlatformUserService platformUserService;
    private final PlatformRoleService platformRoleService;
    private final ObjectUiIconService objectUiIconService;
    private final DeviceProvisioningService deviceProvisioningService;
    private final AutomationTreeService automationTreeService;
    private final ObjectAccessService objectAccessService;
    private final VariableMemberAccessService variableMemberAccessService;
    private final TenantScopeService tenantScopeService;
    private final TenantVirtualRootService tenantVirtualRootService;
    private final TenantQuotaService tenantQuotaService;
    private final FederationProxyService federationProxyService;
    private final FederationBindService federationBindService;
    private final ObjectMapper objectMapper;
    private final ObjectEditLeaseService editLeaseService;
    private final BlueprintRegistry blueprintRegistry;
    private final BlueprintApplicationService blueprintApplicationService;
    private final VisualGroupService visualGroupService;
    private final ObjectBulkDeleteService objectBulkDeleteService;
    private final DriverRuntimeService driverRuntimeService;
    private final AuditEventService auditEventService;
    private final SystemObjectStructureService systemObjectStructureService;
    private final ObjectSearchService objectSearchService;

    public ObjectController(
            ObjectManager objectManager,
            ObjectTemplateService objectTemplateService,
            DashboardService dashboardService,
            ReportService reportService,
            WorkflowService workflowService,
            PlatformUserService platformUserService,
            PlatformRoleService platformRoleService,
            ObjectUiIconService objectUiIconService,
            DeviceProvisioningService deviceProvisioningService,
            AutomationTreeService automationTreeService,
            ObjectAccessService objectAccessService,
            VariableMemberAccessService variableMemberAccessService,
            TenantScopeService tenantScopeService,
            TenantVirtualRootService tenantVirtualRootService,
            TenantQuotaService tenantQuotaService,
            FederationProxyService federationProxyService,
            FederationBindService federationBindService,
            ObjectMapper objectMapper,
            ObjectEditLeaseService editLeaseService,
            BlueprintRegistry blueprintRegistry,
            BlueprintApplicationService blueprintApplicationService,
            VisualGroupService visualGroupService,
            ObjectBulkDeleteService objectBulkDeleteService,
            DriverRuntimeService driverRuntimeService,
            AuditEventService auditEventService,
            SystemObjectStructureService systemObjectStructureService,
            ObjectSearchService objectSearchService
    ) {
        this.objectManager = objectManager;
        this.objectTemplateService = objectTemplateService;
        this.dashboardService = dashboardService;
        this.reportService = reportService;
        this.workflowService = workflowService;
        this.platformUserService = platformUserService;
        this.platformRoleService = platformRoleService;
        this.objectUiIconService = objectUiIconService;
        this.deviceProvisioningService = deviceProvisioningService;
        this.automationTreeService = automationTreeService;
        this.objectAccessService = objectAccessService;
        this.variableMemberAccessService = variableMemberAccessService;
        this.tenantScopeService = tenantScopeService;
        this.tenantVirtualRootService = tenantVirtualRootService;
        this.tenantQuotaService = tenantQuotaService;
        this.federationProxyService = federationProxyService;
        this.federationBindService = federationBindService;
        this.objectMapper = objectMapper;
        this.editLeaseService = editLeaseService;
        this.blueprintRegistry = blueprintRegistry;
        this.blueprintApplicationService = blueprintApplicationService;
        this.visualGroupService = visualGroupService;
        this.objectBulkDeleteService = objectBulkDeleteService;
        this.driverRuntimeService = driverRuntimeService;
        this.auditEventService = auditEventService;
        this.systemObjectStructureService = systemObjectStructureService;
        this.objectSearchService = objectSearchService;
    }

    private void requireObjectTreeReady() {
        if (!objectManager.isInitialized()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Object tree is initializing");
        }
    }

    private ObjectDto toDto(PlatformObject node) {
        return ObjectDto.from(
                node,
                objectUiIconService.readIconId(node).orElse(null),
                AppliedBlueprintDto.resolve(node, blueprintRegistry)
        );
    }

    private ObjectDto toLiteDto(PlatformObject node) {
        ObjectDto dto = ObjectDto.fromLite(
                node,
                objectUiIconService.readIconId(node).orElse(null),
                AppliedBlueprintDto.resolve(node, blueprintRegistry)
        );
        return ObjectTreeDriverEnricher.enrichLite(dto, node, driverRuntimeService);
    }

    private void beginWrite(String path, Authentication authentication, HttpHeaders headers) {
        tenantScopeService.requirePathInScope(path, authentication);
        objectAccessService.requireWrite(path, authentication);
        editLeaseService.assertWritable(path, authentication != null ? authentication.getName() : "system");
        ObjectCollaborationSupport.bindWriteContext(authentication, headers);
    }

    private void endWrite() {
        ObjectCollaborationSupport.clearContext();
    }

    private String canonicalPath(String path, Authentication authentication) {
        return tenantVirtualRootService.toCanonical(path, authentication);
    }

    private ObjectDto virtualizeDto(ObjectDto dto, Authentication authentication) {
        return tenantVirtualRootService.virtualize(dto, authentication);
    }


    @GetMapping("/by-path/audit")
    public List<Map<String, Object>> audit(
            @RequestParam String path,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        path = canonicalPath(path, authentication);
        tenantScopeService.requirePathInScope(path, authentication);
        objectAccessService.requireRead(path, authentication);
        return objectManager.configAudit(path, limit).stream()
                .map(entry -> Map.<String, Object>of(
                        "id", entry.id(),
                        "objectPath", Objects.requireNonNullElse(
                                tenantVirtualRootService.toVirtual(entry.objectPath(), authentication),
                                entry.objectPath()),
                        "changeType", entry.changeType(),
                        "field", entry.field() != null ? entry.field() : "",
                        "actor", entry.actor() != null ? entry.actor() : "",
                        "occurredAt", entry.occurredAt().toString(),
                        "revisionBefore", entry.revisionBefore(),
                        "revisionAfter", entry.revisionAfter(),
                        "summaryJson", entry.summaryJson() != null ? entry.summaryJson() : ""
                ))
                .toList();
    }

    // Edit leases: see ObjectEditLeaseController. Functions/events: see ObjectBehaviorController.

    @GetMapping
    public List<ObjectDto> list(
            @RequestParam(required = false) String parent,
            @RequestParam(defaultValue = "false") boolean lite,
            Authentication authentication
    ) {
        requireObjectTreeReady();
        var tree = objectManager.tree();
        java.util.function.Function<PlatformObject, ObjectDto> mapper = lite
                ? this::toLiteDto
                : this::toDto;
        if (parent == null || parent.isBlank()) {
            return tenantVirtualRootService.virtualize(
                    tree.all().stream()
                            .filter(node -> tenantScopeService.isPathVisible(node.path(), authentication))
                            .filter(node -> objectAccessService.canRead(node.path(), authentication))
                            .map(mapper)
                            .toList(),
                    authentication
            );
        }
        if (!tenantScopeService.isPathVisible(parent, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + parent);
        }
        String canonicalParent = canonicalPath(parent, authentication);
        // Sole-tenant: children of root are only the virtual platform node.
        if ("root".equals(canonicalParent) && tenantVirtualRootService.isActive(authentication)) {
            String platformPath = TenantPaths.tenantPlatform(
                    tenantVirtualRootService.activeTenantId(authentication).orElseThrow()
            );
            if (!objectAccessService.canRead(platformPath, authentication)) {
                return List.of();
            }
            return tenantVirtualRootService.virtualize(
                    List.of(mapper.apply(objectManager.require(platformPath))),
                    authentication
            );
        }
        objectAccessService.requireRead(canonicalParent, authentication);
        PlatformObject parentNode = objectManager.require(canonicalParent);
        if (parentNode.type() == ObjectType.VISUAL_GROUP) {
            return tenantVirtualRootService.virtualize(
                    resolveVisualGroupMembers(canonicalParent, authentication, lite),
                    authentication
            );
        }
        return tenantVirtualRootService.virtualize(
                tree.childrenOf(canonicalParent).stream()
                        .filter(node -> tenantScopeService.isPathVisible(node.path(), authentication))
                        .filter(node -> objectAccessService.canRead(node.path(), authentication))
                        .filter(node -> !visualGroupService.isHiddenFromStructuralTree(node.path()))
                        .map(mapper)
                        .toList(),
                authentication
        );
    }

    @GetMapping("/search")
    public ObjectSearchResponse search(
            @RequestParam String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String parentPrefix,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication
    ) {
        requireObjectTreeReady();
        String prefix = parentPrefix == null || parentPrefix.isBlank()
                ? ""
                : canonicalPath(parentPrefix, authentication);
        ObjectSearchService.Result result = objectSearchService.search(q, type, prefix, limit, authentication);
        List<ObjectDto> objects = tenantVirtualRootService.virtualize(
                result.nodes().stream().map(this::toLiteDto).toList(),
                authentication
        );
        return new ObjectSearchResponse(result.query(), result.matchCount(), result.truncated(), objects);
    }

    public record ObjectSearchResponse(
            String query,
            int matchCount,
            boolean truncated,
            List<ObjectDto> objects
    ) {
    }

    @GetMapping("/by-path/editor")
    public ObjectEditorDto editor(@RequestParam String path, Authentication authentication) {
        requireObjectTreeReady();
        if (!tenantScopeService.isPathVisible(path, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + path);
        }
        path = canonicalPath(path, authentication);
        objectAccessService.requireRead(path, authentication);
        return tenantVirtualRootService.virtualize(
                ObjectEditorDto.from(
                        objectManager.require(path),
                        objectUiIconService,
                        authentication,
                        variableMemberAccessService
                ),
                authentication
        );
    }

    @GetMapping("/by-path")
    public ObjectDto get(@RequestParam String path, Authentication authentication) {
        requireObjectTreeReady();
        if (!tenantScopeService.isPathVisible(path, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + path);
        }
        final String canonical = canonicalPath(path, authentication);
        objectAccessService.requireRead(canonical, authentication);
        ObjectDto dto = federationProxyService.resolve(canonical)
                .map(target -> mapProxyObject(canonical, target))
                .orElseGet(() -> toDto(objectManager.require(canonical)));
        return virtualizeDto(dto, authentication);
    }

    @PostMapping
    public ObjectDto create(@Valid @RequestBody CreateObjectRequest request, Authentication authentication) {
        String parentPath = canonicalPath(request.parentPath(), authentication);
        tenantScopeService.requirePathInScope(parentPath, authentication);
        tenantQuotaService.assertCanCreate(parentPath, request.type());
        objectAccessService.requireWrite(parentPath, authentication);
        federationBindService.assertParentAllowsChildren(parentPath);
        String fullPath = objectManager.tree().resolveChildPath(parentPath, request.name());
        if (objectManager.tree().findByPath(fullPath).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object exists: " + fullPath);
        }
        PlatformObject node = objectManager.create(
                parentPath,
                request.name(),
                request.type(),
                request.displayName(),
                request.description(),
                request.templateId()
        );
        objectTemplateService.applyTemplate(node.path(), request.templateId());
        boolean autoApplyMixin = request.autoApplyMixinBlueprints() == null
                || Boolean.TRUE.equals(request.autoApplyMixinBlueprints());
        if (autoApplyMixin) {
            blueprintApplicationService.applyMixinBlueprintsWithRules(node.path());
        }
        if (request.type() == ObjectType.DASHBOARD) {
            dashboardService.ensureDashboardStructure(node.path());
        }
        if (request.type() == ObjectType.REPORT) {
            reportService.ensureReportStructure(node.path());
        }
        if (request.type() == ObjectType.WORKFLOW) {
            workflowService.ensureWorkflowStructure(node.path());
        }
        if (request.type() == ObjectType.ALERT) {
            automationTreeService.ensureAlertRuleStructure(node.path());
        }
        if (request.type() == ObjectType.CORRELATOR) {
            automationTreeService.ensureCorrelatorStructure(node.path());
        }
        ensurePhase30CatalogStructure(node);
        if (request.type() == ObjectType.DEVICE && request.driverId() != null && !request.driverId().isBlank()) {
            deviceProvisioningService.provisionDriver(
                    node.path(),
                    request.driverId(),
                    request.driverPollIntervalMs(),
                    Boolean.TRUE.equals(request.autoStartDriver())
                            || request.autoStartDriver() == null
            );
            objectManager.persistNodeTree(node.path());
        }
        return virtualizeDto(toDto(node), authentication);
    }

    @PatchMapping("/by-path")
    public ObjectDto update(
            @RequestParam String path,
            @Valid @RequestBody UpdateObjectRequest request,
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestHeader HttpHeaders headers
    ) {
        path = canonicalPath(path, authentication);
        beginWrite(path, authentication, headers);
        try {
            if (request.iconId() != null) {
                objectUiIconService.setIconId(path, request.iconId());
            }
            PlatformObject node = objectManager.require(path);
            if (request.displayName() != null || request.description() != null) {
                node = objectManager.updateInfo(
                        path,
                        request.displayName() != null ? request.displayName() : node.displayName(),
                        request.description() != null ? request.description() : node.description()
                );
            }
            if (request.bindingAuditEnabled() != null) {
                node = objectManager.updateBindingAuditEnabled(path, request.bindingAuditEnabled());
            }
            if (request.functionAuditEnabled() != null) {
                node = objectManager.updateFunctionAuditEnabled(path, request.functionAuditEnabled());
            }
            if (request.eventJournalEnabled() != null) {
                node = objectManager.updateEventJournalEnabled(path, request.eventJournalEnabled());
            }
            return virtualizeDto(toDto(node), authentication);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @PostMapping("/bulk-delete")
    public ObjectBulkDeleteService.BulkDeleteResult bulkDelete(
            @Valid @RequestBody BulkDeleteRequest request,
            Authentication authentication
    ) {
        if (request.paths() == null || request.paths().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paths is required");
        }
        List<String> canonicalPaths = new ArrayList<>();
        for (String path : request.paths()) {
            if (path != null && !path.isBlank()) {
                String canonical = canonicalPath(path.trim(), authentication);
                tenantScopeService.requirePathInScope(canonical, authentication);
                objectAccessService.requireWrite(canonical, authentication);
                canonicalPaths.add(canonical);
            }
        }
        return objectBulkDeleteService.deleteAll(canonicalPaths);
    }

    public record BulkDeleteRequest(@NotEmpty List<@NotBlank String> paths) {
    }

    private List<ObjectDto> resolveVisualGroupMembers(
            String groupPath,
            Authentication authentication,
            boolean lite
    ) {
        List<ObjectDto> result = new ArrayList<>();
        for (var member : visualGroupService.listMembers(groupPath)) {
            var optionalNode = objectManager.tree().findByPath(member.path());
            if (optionalNode.isEmpty()) {
                result.add(ObjectDto.missingGroupMember(member.path(), groupPath, member.sortOrder()));
                continue;
            }
            PlatformObject node = optionalNode.get();
            if (!tenantScopeService.isPathVisible(node.path(), authentication)
                    || !objectAccessService.canRead(node.path(), authentication)) {
                continue;
            }
            String iconId = objectUiIconService.readIconId(node).orElse(null);
            result.add(ObjectDto.asGroupMember(node, groupPath, member.sortOrder(), iconId, false));
        }
        return result;
    }

    @DeleteMapping("/by-path")
    public void delete(@RequestParam String path, Authentication authentication) {
        path = canonicalPath(path, authentication);
        tenantScopeService.requirePathInScope(path, authentication);
        objectAccessService.requireWrite(path, authentication);
        String actor = authentication != null ? authentication.getName() : "system";
        try {
            if (platformUserService.isSecurityUserPath(path)) {
                platformUserService.deleteUser(platformUserService.usernameFromPath(path));
                auditEventService.logObjectDeleted(actor, path);
                return;
            }
            if (platformRoleService.isSecurityRolePath(path)) {
                platformRoleService.deleteRole(platformRoleService.roleNameFromPath(path));
                auditEventService.logObjectDeleted(actor, path);
                return;
            }
            if (objectManager.tree().findByPath(path)
                    .filter(node -> node.type() == ObjectType.CORRELATOR)
                    .isPresent()) {
                automationTreeService.deleteCorrelator(path);
                auditEventService.logObjectDeleted(actor, path);
                return;
            }
            objectManager.delete(path);
            auditEventService.logObjectDeleted(actor, path);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ObjectNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody ReorderObjectsRequest request, Authentication authentication) {
        String parentPath = canonicalPath(request.parentPath(), authentication);
        List<String> ordered = request.orderedPaths().stream()
                .map(path -> canonicalPath(path, authentication))
                .toList();
        tenantScopeService.requirePathInScope(parentPath, authentication);
        objectAccessService.requireWrite(parentPath, authentication);
        try {
            objectManager.reorderChildren(parentPath, ordered);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ObjectNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public record CreateObjectRequest(
            @NotBlank String parentPath,
            @NotBlank String name,
            ObjectType type,
            String displayName,
            String description,
            String templateId,
            Boolean autoApplyMixinBlueprints,
            String driverId,
            Integer driverPollIntervalMs,
            Boolean autoStartDriver
    ) {
    }

    public record UpdateObjectRequest(
            String displayName,
            String description,
            String iconId,
            Boolean bindingAuditEnabled,
            Boolean functionAuditEnabled,
            Boolean eventJournalEnabled
    ) {
    }

    public record ReorderObjectsRequest(
            @NotBlank String parentPath,
            @NotEmpty List<@NotBlank String> orderedPaths
    ) {
    }

    private ObjectDto mapProxyObject(String localPath, FederationProxyService.FederationProxyTarget target) {
        JsonNode json = federationProxyService.proxyObject(target);
        ObjectDto remote = objectMapper.convertValue(json, ObjectDto.class);
        return new ObjectDto(
                remote.id(),
                localPath,
                remote.type(),
                remote.displayName(),
                remote.description(),
                remote.templateId(),
                remote.iconId(),
                remote.createdAt(),
                remote.sortOrder(),
                remote.revision(),
                remote.lastChangedBy(),
                remote.lastChangedAt(),
                remote.variableNames(),
                remote.eventNames(),
                true,
                target.peerId().toString(),
                target.remotePath(),
                remote.appliedBlueprints() != null ? remote.appliedBlueprints() : List.of(),
                false,
                null,
                false,
                remote.driverStatus(),
                remote.driverConnected(),
                remote.bindingAuditEnabled(),
                remote.functionAuditEnabled(),
                remote.eventJournalEnabled()
        );
    }

    private void ensurePhase30CatalogStructure(PlatformObject node) {
        String path = node.path();
        String leafId = path.substring(path.lastIndexOf('.') + 1);
        DataSchema stringSchema = DataSchema.builder("stringValue").field("value", com.ispf.core.model.FieldType.STRING).build();
        switch (node.type()) {
            case EVENT_FILTER -> {
                systemObjectStructureService.ensureEventFilterStructure(path);
                setStringVariable(path, "filterId", leafId, stringSchema);
            }
            case PROCESS_PROGRAM -> {
                systemObjectStructureService.ensureProcessProgramStructure(path);
                setStringVariable(path, "programId", leafId, stringSchema);
            }
            case ANALYTICS_TEMPLATE -> {
                systemObjectStructureService.ensureAnalyticsTemplateStructure(path);
                setStringVariable(path, "templateId", leafId, stringSchema);
            }
            default -> {
            }
        }
    }

    private void setStringVariable(String path, String name, String value, DataSchema schema) {
        objectManager.setVariableValue(path, name, DataRecord.single(schema, Map.of("value", value != null ? value : "")));
    }
}
