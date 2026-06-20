package com.ispf.server.api;

import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.server.api.dto.ObjectDto;
import com.ispf.server.api.dto.ObjectEditorDto;
import com.ispf.server.api.dto.VariableDto;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectUiIconService;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.security.PlatformRoleService;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.workflow.WorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/objects")
public class ObjectController {

    private final ObjectManager objectManager;
    private final DashboardService dashboardService;
    private final WorkflowService workflowService;
    private final PlatformUserService platformUserService;
    private final PlatformRoleService platformRoleService;
    private final ObjectUiIconService objectUiIconService;
    private final DeviceProvisioningService deviceProvisioningService;
    private final AutomationTreeService automationTreeService;

    public ObjectController(
            ObjectManager objectManager,
            DashboardService dashboardService,
            WorkflowService workflowService,
            PlatformUserService platformUserService,
            PlatformRoleService platformRoleService,
            ObjectUiIconService objectUiIconService,
            DeviceProvisioningService deviceProvisioningService,
            AutomationTreeService automationTreeService
    ) {
        this.objectManager = objectManager;
        this.dashboardService = dashboardService;
        this.workflowService = workflowService;
        this.platformUserService = platformUserService;
        this.platformRoleService = platformRoleService;
        this.objectUiIconService = objectUiIconService;
        this.deviceProvisioningService = deviceProvisioningService;
        this.automationTreeService = automationTreeService;
    }

    private ObjectDto toDto(PlatformObject node) {
        return ObjectDto.from(node, objectUiIconService.readIconId(node).orElse(null));
    }

    @GetMapping
    public List<ObjectDto> list(@RequestParam(required = false) String parent) {
        var tree = objectManager.tree();
        if (parent == null || parent.isBlank()) {
            return tree.all().stream().map(this::toDto).toList();
        }
        return tree.childrenOf(parent).stream().map(this::toDto).toList();
    }

    @GetMapping("/by-path/editor")
    public ObjectEditorDto editor(@RequestParam String path) {
        return ObjectEditorDto.from(objectManager.require(path), objectUiIconService);
    }

    @GetMapping("/by-path")
    public ObjectDto get(@RequestParam String path) {
        return toDto(objectManager.require(path));
    }

    @PostMapping
    public ObjectDto create(@Valid @RequestBody CreateObjectRequest request) {
        String fullPath = objectManager.tree().resolveChildPath(request.parentPath(), request.name());
        if (objectManager.tree().findByPath(fullPath).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Object exists: " + fullPath);
        }
        PlatformObject node = objectManager.create(
                request.parentPath(),
                request.name(),
                request.type(),
                request.displayName(),
                request.description(),
                request.templateId()
        );
        if (request.type() == ObjectType.DASHBOARD) {
            dashboardService.ensureDashboardStructure(node.path());
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
        if (request.type() == ObjectType.DEVICE && request.driverId() != null && !request.driverId().isBlank()) {
            deviceProvisioningService.provisionDriver(
                    node.path(),
                    request.driverId(),
                    request.driverPollIntervalMs(),
                    Boolean.TRUE.equals(request.autoStartDriver())
            );
            objectManager.persistNodeTree(node.path());
        }
        return toDto(objectManager.require(node.path()));
    }

    @PatchMapping("/by-path")
    public ObjectDto update(@RequestParam String path, @Valid @RequestBody UpdateObjectRequest request) {
        try {
            if (request.iconId() != null) {
                objectUiIconService.setIconId(path, request.iconId());
            }
            PlatformObject node = objectManager.updateInfo(path, request.displayName(), request.description());
            return toDto(node);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/by-path")
    public void delete(@RequestParam String path) {
        try {
            if (platformUserService.isSecurityUserPath(path)) {
                platformUserService.deleteUser(platformUserService.usernameFromPath(path));
                return;
            }
            if (platformRoleService.isSecurityRolePath(path)) {
                platformRoleService.deleteRole(platformRoleService.roleNameFromPath(path));
                return;
            }
            if (objectManager.tree().findByPath(path)
                    .filter(node -> node.type() == ObjectType.CORRELATOR)
                    .isPresent()) {
                automationTreeService.deleteCorrelator(path);
                return;
            }
            objectManager.delete(path);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ObjectNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody ReorderObjectsRequest request) {
        try {
            objectManager.reorderChildren(request.parentPath(), request.orderedPaths());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ObjectNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/by-path/variables")
    public List<VariableDto> listVariables(@RequestParam String path) {
        PlatformObject node = objectManager.require(path);
        return node.variables().values().stream().map(VariableDto::from).toList();
    }

    @GetMapping("/by-path/variables/detail")
    public VariableDto getVariable(@RequestParam String path, @RequestParam String name) {
        PlatformObject node = objectManager.require(path);
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable: " + name));
        return VariableDto.from(variable);
    }

    @PutMapping("/by-path/variables")
    public VariableDto setVariable(
            @RequestParam String path,
            @RequestParam String name,
            @RequestBody DataRecord value
    ) {
        try {
            Variable variable = objectManager.setVariableValue(path, name, value);
            if (platformUserService.isSecurityUserPath(path)) {
                platformUserService.syncVariableFromObject(path, name, value);
            }
            return VariableDto.from(variable);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PatchMapping("/by-path/variables/history")
    public VariableDto updateVariableHistory(
            @RequestParam String path,
            @RequestParam String name,
            @Valid @RequestBody UpdateVariableHistoryRequest request
    ) {
        try {
            Variable variable = objectManager.updateVariableHistory(
                    path,
                    name,
                    request.historyEnabled(),
                    request.historyRetentionDays()
            );
            return VariableDto.from(variable);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public record CreateObjectRequest(
            @NotBlank String parentPath,
            @NotBlank String name,
            ObjectType type,
            String displayName,
            String description,
            String templateId,
            String driverId,
            Integer driverPollIntervalMs,
            Boolean autoStartDriver
    ) {
    }

    public record UpdateObjectRequest(
            String displayName,
            String description,
            String iconId
    ) {
    }

    public record ReorderObjectsRequest(
            @NotBlank String parentPath,
            @NotEmpty List<@NotBlank String> orderedPaths
    ) {
    }

    public record UpdateVariableHistoryRequest(
            boolean historyEnabled,
            Integer historyRetentionDays
    ) {
    }
}
