package com.ispf.server.api;

import com.ispf.server.datasource.ConnectionTestResult;
import com.ispf.server.datasource.DataSourceConnectionResolver;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.datasource.DataSourcePathResolver;
import com.ispf.server.datasource.DataSourceQueryResult;
import com.ispf.server.tenant.TenantLocalDataAccessGuard;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.tenant.TenantVirtualRootService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/data-sources")
public class DataSourceController {

    private final DataSourceObjectService dataSourceObjectService;
    private final TenantScopeService tenantScopeService;
    private final TenantLocalDataAccessGuard tenantLocalDataAccessGuard;
    private final TenantVirtualRootService tenantVirtualRootService;

    public DataSourceController(
            DataSourceObjectService dataSourceObjectService,
            TenantScopeService tenantScopeService,
            TenantLocalDataAccessGuard tenantLocalDataAccessGuard,
            TenantVirtualRootService tenantVirtualRootService
    ) {
        this.dataSourceObjectService = dataSourceObjectService;
        this.tenantScopeService = tenantScopeService;
        this.tenantLocalDataAccessGuard = tenantLocalDataAccessGuard;
        this.tenantVirtualRootService = tenantVirtualRootService;
    }

    @GetMapping("/by-path")
    public DataSourceObjectService.DataSourceView get(@RequestParam String path, Authentication authentication) {
        String canonical = requirePathAccess(path, authentication);
        return dataSourceObjectService.getByPath(canonical);
    }

    @PostMapping
    public DataSourceObjectService.DataSourceView create(
            @RequestBody CreateDataSourceRequest request,
            Authentication authentication
    ) {
        String path = tenantVirtualRootService.dataSourcesRoot(authentication) + "."
                + DataSourcePathResolver.sanitizeNodeName(request.name());
        requirePathAccess(path, authentication);
        tenantLocalDataAccessGuard.requireExternalConnectionMode(request.connectionMode(), authentication);
        if (DataSourceConnectionResolver.MODE_EXTERNAL.equalsIgnoreCase(
                request.connectionMode() != null ? request.connectionMode() : "")) {
            tenantLocalDataAccessGuard.requireAllowedJdbcUrl(request.jdbcUrl(), authentication);
        }
        return dataSourceObjectService.create(request.toWriteRequest());
    }

    @PutMapping("/by-path")
    public DataSourceObjectService.DataSourceView update(
            @RequestParam String path,
            @RequestBody UpdateDataSourceRequest request,
            Authentication authentication
    ) {
        String canonical = requirePathAccess(path, authentication);
        if (request.connectionMode() != null) {
            tenantLocalDataAccessGuard.requireExternalConnectionMode(request.connectionMode(), authentication);
        }
        if (request.jdbcUrl() != null && !request.jdbcUrl().isBlank()) {
            tenantLocalDataAccessGuard.requireAllowedJdbcUrl(request.jdbcUrl(), authentication);
        }
        return dataSourceObjectService.update(canonical, request.toWriteRequest());
    }

    @PostMapping("/by-path/test-connection")
    public Map<String, Object> testConnection(
            @RequestParam String path,
            @RequestBody(required = false) TestConnectionRequest request,
            Authentication authentication
    ) {
        String canonical = requirePathAccess(path, authentication);
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(canonical, authentication);
        if (request != null && request.jdbcUrl() != null && !request.jdbcUrl().isBlank()) {
            tenantLocalDataAccessGuard.requireAllowedJdbcUrl(request.jdbcUrl(), authentication);
        }
        DataSourceConnectionResolver.ExternalConfigProbe probe = request != null
                ? request.toProbe()
                : null;
        ConnectionTestResult result = dataSourceObjectService.testConnection(canonical, probe);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", Objects.requireNonNullElse(
                tenantVirtualRootService.toVirtual(canonical, authentication),
                path
        ));
        body.put("connected", result.connected());
        if (result.message() != null) {
            body.put("message", result.message());
        }
        return body;
    }

    @PostMapping("/by-path/execute-query")
    public DataSourceQueryResult executeQuery(
            @RequestParam String path,
            @RequestBody ExecuteQueryRequest request,
            Authentication authentication
    ) {
        String canonical = requirePathAccess(path, authentication);
        tenantLocalDataAccessGuard.requireAllowedDataSourcePath(canonical, authentication);
        return dataSourceObjectService.executeQuery(
                canonical,
                request.query(),
                request.params(),
                request.maxRows()
        );
    }

    private String requirePathAccess(String path, Authentication authentication) {
        String canonical = tenantVirtualRootService.toCanonical(path, authentication);
        tenantScopeService.requirePathInScope(canonical, authentication);
        return canonical;
    }

    public record ExecuteQueryRequest(
            @NotBlank String query,
            List<Object> params,
            Integer maxRows
    ) {
    }

    public record TestConnectionRequest(
            String jdbcUrl,
            String jdbcDriverClass,
            String jdbcUsername,
            String jdbcPassword,
            Integer poolSize
    ) {
        DataSourceConnectionResolver.ExternalConfigProbe toProbe() {
            return new DataSourceConnectionResolver.ExternalConfigProbe(
                    jdbcUrl, jdbcDriverClass, jdbcUsername, jdbcPassword, poolSize
            );
        }
    }

    public record CreateDataSourceRequest(
            @NotBlank String name,
            String displayName,
            String connectionMode,
            String schemaName,
            String jdbcUrl,
            String jdbcDriverClass,
            String jdbcUsername,
            String jdbcPassword,
            Integer poolSize,
            String description
    ) {
        DataSourceObjectService.DataSourceWriteRequest toWriteRequest() {
            return new DataSourceObjectService.DataSourceWriteRequest(
                    name, displayName, connectionMode, schemaName,
                    jdbcUrl, jdbcDriverClass, jdbcUsername, jdbcPassword, poolSize, description
            );
        }
    }

    public record UpdateDataSourceRequest(
            String displayName,
            String connectionMode,
            String schemaName,
            String jdbcUrl,
            String jdbcDriverClass,
            String jdbcUsername,
            String jdbcPassword,
            Integer poolSize,
            String description
    ) {
        DataSourceObjectService.DataSourceWriteRequest toWriteRequest() {
            return new DataSourceObjectService.DataSourceWriteRequest(
                    null, displayName, connectionMode, schemaName,
                    jdbcUrl, jdbcDriverClass, jdbcUsername, jdbcPassword, poolSize, description
            );
        }
    }
}
