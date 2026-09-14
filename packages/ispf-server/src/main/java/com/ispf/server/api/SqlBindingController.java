package com.ispf.server.api;

import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/sql-bindings")
public class SqlBindingController {

    private final SqlBindingObjectService sqlBindingObjectService;
    private final TenantScopeService tenantScopeService;
    private final TenantVirtualRootService tenantVirtualRootService;
    private final ObjectAccessService objectAccessService;

    public SqlBindingController(
            SqlBindingObjectService sqlBindingObjectService,
            TenantScopeService tenantScopeService,
            TenantVirtualRootService tenantVirtualRootService,
            ObjectAccessService objectAccessService
    ) {
        this.sqlBindingObjectService = sqlBindingObjectService;
        this.tenantScopeService = tenantScopeService;
        this.tenantVirtualRootService = tenantVirtualRootService;
        this.objectAccessService = objectAccessService;
    }

    @GetMapping("/by-path")
    public SqlBindingObjectService.BindingDefinition get(
            @RequestParam String path,
            Authentication authentication
    ) {
        String canonical = requirePathRead(path, authentication);
        return sqlBindingObjectService.getByPath(canonical);
    }

    @PostMapping
    public SqlBindingObjectService.BindingDefinition create(
            @RequestBody SaveSqlBindingRequest request,
            Authentication authentication
    ) {
        if (request.bindingId() == null || request.bindingId().isBlank()) {
            throw new IllegalArgumentException("bindingId is required");
        }
        requirePathWrite(SqlBindingObjectService.BINDINGS_ROOT, authentication);
        String path = sqlBindingObjectService.pathForBindingId(request.bindingId());
        tenantScopeService.requirePathInScope(path, authentication);
        return sqlBindingObjectService.create(toDefinition(path, request));
    }

    @PutMapping("/by-path")
    public SqlBindingObjectService.BindingDefinition update(
            @RequestParam String path,
            @RequestBody SaveSqlBindingRequest request,
            Authentication authentication
    ) {
        String canonical = requirePathWrite(path, authentication);
        return sqlBindingObjectService.update(canonical, toDefinition(canonical, request));
    }

    @PostMapping("/by-path/refresh")
    public Map<String, Object> refresh(@RequestParam String path, Authentication authentication) {
        String canonical = requirePathWrite(path, authentication);
        sqlBindingObjectService.refresh(canonical);
        return Map.of(
                "status", "OK",
                "path", Objects.requireNonNullElse(
                        tenantVirtualRootService.toVirtual(canonical, authentication),
                        path
                ),
                "binding", sqlBindingObjectService.getByPath(canonical)
        );
    }

    private String requirePathRead(String path, Authentication authentication) {
        String canonical = requirePathAccess(path, authentication);
        objectAccessService.requireRead(canonical, authentication);
        return canonical;
    }

    private String requirePathWrite(String path, Authentication authentication) {
        String canonical = requirePathAccess(path, authentication);
        objectAccessService.requireWrite(canonical, authentication);
        return canonical;
    }

    private String requirePathAccess(String path, Authentication authentication) {
        String canonical = tenantVirtualRootService.toCanonical(path, authentication);
        tenantScopeService.requirePathInScope(canonical, authentication);
        return canonical;
    }

    private static SqlBindingObjectService.BindingDefinition toDefinition(
            String path,
            SaveSqlBindingRequest request
    ) {
        String bindingId = request.bindingId() != null && !request.bindingId().isBlank()
                ? request.bindingId()
                : path.substring(path.lastIndexOf('.') + 1);
        return new SqlBindingObjectService.BindingDefinition(
                path,
                bindingId,
                request.targetObjectPath() != null ? request.targetObjectPath() : "",
                request.variable() != null ? request.variable() : "value",
                request.dataSourcePath() != null ? request.dataSourcePath() : "",
                request.query() != null ? request.query() : "",
                request.valueField() != null ? request.valueField() : "value",
                request.refresh() != null ? request.refresh() : "manual",
                request.refreshIntervalMs() != null ? request.refreshIntervalMs() : 30_000L,
                request.triggerObjectPath() != null ? request.triggerObjectPath() : "",
                request.triggerFunctionName() != null ? request.triggerFunctionName() : "",
                request.enabled() == null || request.enabled(),
                null
        );
    }

    public record SaveSqlBindingRequest(
            String bindingId,
            String targetObjectPath,
            String variable,
            String dataSourcePath,
            String query,
            String valueField,
            String refresh,
            Long refreshIntervalMs,
            String triggerObjectPath,
            String triggerFunctionName,
            Boolean enabled
    ) {
    }
}
