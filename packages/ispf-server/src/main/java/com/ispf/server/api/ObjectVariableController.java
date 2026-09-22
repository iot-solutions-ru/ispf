package com.ispf.server.api;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.object.HistorySampleMode;
import com.ispf.core.object.ObjectNotFoundException;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.core.object.VariableStorageMode;
import com.ispf.server.api.dto.VariableDto;
import com.ispf.server.api.support.ObjectCollaborationSupport;
import com.ispf.server.config.IspfRoles;
import com.ispf.server.federation.FederationProxyService;
import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.security.PlatformUserService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.security.acl.VariableMemberAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import com.ispf.server.audit.AuditEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/objects")
public class ObjectVariableController {

    private static final Logger log = LoggerFactory.getLogger(ObjectVariableController.class);

    private final ObjectManager objectManager;
    private final PlatformUserService platformUserService;
    private final ObjectAccessService objectAccessService;
    private final VariableMemberAccessService variableMemberAccessService;
    private final TenantScopeService tenantScopeService;
    private final TenantVirtualRootService tenantVirtualRootService;
    private final FederationProxyService federationProxyService;
    private final ObjectMapper objectMapper;
    private final ObjectEditLeaseService editLeaseService;
    private final AuditEventService auditEventService;

    public ObjectVariableController(
            ObjectManager objectManager,
            PlatformUserService platformUserService,
            ObjectAccessService objectAccessService,
            VariableMemberAccessService variableMemberAccessService,
            TenantScopeService tenantScopeService,
            TenantVirtualRootService tenantVirtualRootService,
            FederationProxyService federationProxyService,
            ObjectMapper objectMapper,
            ObjectEditLeaseService editLeaseService,
            AuditEventService auditEventService
    ) {
        this.objectManager = objectManager;
        this.platformUserService = platformUserService;
        this.objectAccessService = objectAccessService;
        this.variableMemberAccessService = variableMemberAccessService;
        this.tenantScopeService = tenantScopeService;
        this.tenantVirtualRootService = tenantVirtualRootService;
        this.federationProxyService = federationProxyService;
        this.objectMapper = objectMapper;
        this.editLeaseService = editLeaseService;
        this.auditEventService = auditEventService;
    }

    @GetMapping("/by-path/variables")
    public List<VariableDto> listVariables(@RequestParam String path, Authentication authentication) {
        if (!tenantScopeService.isPathVisible(path, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant scope denied for " + path);
        }
        final String canonical = canonicalPath(path, authentication);
        objectAccessService.requireRead(canonical, authentication);
        var proxy = federationProxyService.resolve(canonical);
        if (proxy.isPresent()) {
            JsonNode json = federationProxyService.proxyVariables(proxy.get());
            return filterProxyVariables(canonical, json, authentication);
        }
        PlatformObject node = objectManager.require(canonical);
        return variableMemberAccessService
                .filterReadable(canonical, node.variables().values(), authentication)
                .stream()
                .map(VariableDto::from)
                .toList();
    }

    @GetMapping("/variables/batch")
    public Map<String, List<VariableDto>> listVariablesBatch(
            @RequestParam String paths,
            Authentication authentication
    ) {
        String[] pathArray = paths.split(",");
        if (pathArray.length > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max 50 paths per batch request");
        }
        Map<String, List<VariableDto>> result = new LinkedHashMap<>();
        for (String rawPath : pathArray) {
            String requested = rawPath.trim();
            if (requested.isBlank()) {
                continue;
            }
            if (!tenantScopeService.isPathVisible(requested, authentication)) {
                continue;
            }
            String path = canonicalPath(requested, authentication);
            String responseKey = tenantVirtualRootService.toVirtual(path, authentication);
            if (responseKey == null) {
                responseKey = requested;
            }
            if (!objectAccessService.canRead(path, authentication)) {
                continue;
            }
            try {
                var proxy = federationProxyService.resolve(path);
                if (proxy.isPresent()) {
                    JsonNode json = federationProxyService.proxyVariables(proxy.get());
                    result.put(responseKey, filterProxyVariables(path, json, authentication));
                } else {
                    PlatformObject node = objectManager.require(path);
                    result.put(responseKey, variableMemberAccessService
                            .filterReadable(path, node.variables().values(), authentication)
                            .stream()
                            .map(VariableDto::from)
                            .toList());
                }
            } catch (ObjectNotFoundException e) {
                // omit paths that do not exist
            }
        }
        return result;
    }

    @GetMapping("/by-path/variables/detail")
    public VariableDto getVariable(
            @RequestParam String path,
            @RequestParam String name,
            Authentication authentication
    ) {
        path = canonicalPath(path, authentication);
        tenantScopeService.requirePathInScope(path, authentication);
        objectAccessService.requireRead(path, authentication);
        PlatformObject node = objectManager.require(path);
        Variable variable = node.getVariable(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable: " + name));
        objectAccessService.requireVariableRead(path, name, variable.readRoles(), authentication);
        return VariableDto.from(variable);
    }

    @PutMapping("/by-path/variables")
    public VariableDto setVariable(
            @RequestParam String path,
            @RequestParam String name,
            @RequestBody DataRecord value,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        path = canonicalPath(path, authentication);
        beginWrite(path, authentication, headers);
        try {
            var proxy = federationProxyService.resolve(path);
            if (proxy.isPresent()) {
                try {
                    PlatformObject localNode = objectManager.require(path);
                    Variable localVariable = localNode.getVariable(name).orElse(null);
                    if (localVariable != null) {
                        variableMemberAccessService.requireWrite(localVariable, path, authentication);
                    } else if (!IspfRoles.isGlobalAdmin(authentication)) {
                        log.warn(
                                "Denied proxy variable write for {} on {} because local role metadata is missing",
                                name,
                                path
                        );
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Proxy variable has no local ACL metadata: " + name
                        );
                    }
                    JsonNode json = federationProxyService.proxyVariablePut(
                            proxy.get(),
                            name,
                            objectMapper.writeValueAsString(value)
                    );
                    return objectMapper.convertValue(json, VariableDto.class);
                } catch (tools.jackson.core.JacksonException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
                }
            }
            PlatformObject node = objectManager.require(path);
            Variable existing = node.getVariable(name)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variable: " + name));
            variableMemberAccessService.requireWrite(existing, path, authentication);
            Variable variable = objectManager.setVariableValue(path, name, value);
            if (platformUserService.isSecurityUserPath(path)) {
                platformUserService.syncVariableFromObject(path, name, value);
            }
            return VariableDto.from(variable);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @PatchMapping("/by-path/variables/history")
    public VariableDto updateVariableHistory(
            @RequestParam String path,
            @RequestParam String name,
            @Valid @RequestBody UpdateVariableHistoryRequest request,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        path = canonicalPath(path, authentication);
        beginWrite(path, authentication, headers);
        try {
            Variable variable = objectManager.updateVariableHistory(
                    path,
                    name,
                    request.historyEnabled(),
                    request.historyRetentionDays(),
                    request.telemetryPublishMode(),
                    request.historySampleMode() != null
                            ? HistorySampleMode.parse(request.historySampleMode())
                            : null,
                    request.includePreviousValueInEvent(),
                    request.storageMode() != null
                            ? VariableStorageMode.parse(request.storageMode())
                            : null
            );
            return VariableDto.from(variable);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @PostMapping("/by-path/variables")
    public VariableDto createVariable(
            @RequestParam String path,
            @Valid @RequestBody CreateVariableRequest request,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        path = canonicalPath(path, authentication);
        beginWrite(path, authentication, headers);
        try {
            assertNotFederationBound(path);
            Variable variable = objectManager.createVariable(
                    path,
                    request.name(),
                    request.schema(),
                    request.readable(),
                    request.writable(),
                    request.initialValue(),
                    request.historyEnabled(),
                    request.historyRetentionDays(),
                    request.readRoles() != null ? request.readRoles() : List.of(),
                    request.writeRoles() != null ? request.writeRoles() : List.of()
            );
            return VariableDto.from(variable);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @PatchMapping("/by-path/variables")
    public VariableDto updateVariableDefinition(
            @RequestParam String path,
            @RequestParam String name,
            @Valid @RequestBody UpdateVariableDefinitionRequest request,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        path = canonicalPath(path, authentication);
        beginWrite(path, authentication, headers);
        try {
            Variable variable = objectManager.updateVariableDefinition(
                    path,
                    name,
                    request.readable(),
                    request.writable(),
                    request.readRoles(),
                    request.writeRoles()
            );
            if (request.readRoles() != null || request.writeRoles() != null) {
                auditEventService.logVariableAclChange(
                        authentication != null ? authentication.getName() : "system",
                        path,
                        name,
                        request.readRoles(),
                        request.writeRoles()
                );
            }
            return VariableDto.from(variable);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    @DeleteMapping("/by-path/variables")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVariable(
            @RequestParam String path,
            @RequestParam String name,
            Authentication authentication,
            @RequestHeader HttpHeaders headers
    ) {
        path = canonicalPath(path, authentication);
        beginWrite(path, authentication, headers);
        try {
            objectManager.deleteVariable(path, name);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } finally {
            endWrite();
        }
    }

    public record CreateVariableRequest(
            @NotBlank String name,
            DataSchema schema,
            boolean readable,
            boolean writable,
            DataRecord initialValue,
            boolean historyEnabled,
            Integer historyRetentionDays,
            List<String> readRoles,
            List<String> writeRoles
    ) {
        public CreateVariableRequest {
            if (schema == null) {
                schema = DataSchema.builder(name != null ? name : "value")
                        .field("value", com.ispf.core.model.FieldType.STRING)
                        .build();
            }
        }
    }

    public record UpdateVariableDefinitionRequest(
            Boolean readable,
            Boolean writable,
            List<String> readRoles,
            List<String> writeRoles
    ) {
    }

    public record UpdateVariableHistoryRequest(
            boolean historyEnabled,
            Integer historyRetentionDays,
            String telemetryPublishMode,
            String historySampleMode,
            Boolean includePreviousValueInEvent,
            String storageMode
    ) {
    }

    private List<VariableDto> filterProxyVariables(
            String localPath,
            JsonNode proxyResponse,
            Authentication authentication
    ) {
        List<VariableDto> remoteVariables = objectMapper.convertValue(
                proxyResponse,
                new TypeReference<List<VariableDto>>() { }
        );
        PlatformObject localNode = objectManager.tree().findByPath(localPath).orElse(null);
        if (localNode == null) {
            return List.of();
        }
        return remoteVariables.stream()
                .filter(variable -> variable.name() != null)
                .filter(variable -> localNode.getVariable(variable.name())
                        .map(localVariable -> variableMemberAccessService.canRead(
                                localPath,
                                localVariable.name(),
                                authentication
                        ))
                        .orElse(false))
                .toList();
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

    private void assertNotFederationBound(String path) {
        if (federationProxyService.resolve(path).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Federation-bound objects use remote variables: " + path
            );
        }
    }
}
