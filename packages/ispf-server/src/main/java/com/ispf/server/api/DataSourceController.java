package com.ispf.server.api;

import com.ispf.server.datasource.ConnectionTestResult;
import com.ispf.server.datasource.DataSourceConnectionResolver;
import com.ispf.server.datasource.DataSourceObjectService;
import com.ispf.server.datasource.DataSourceQueryResult;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/v1/data-sources")
public class DataSourceController {

    private final DataSourceObjectService dataSourceObjectService;

    public DataSourceController(DataSourceObjectService dataSourceObjectService) {
        this.dataSourceObjectService = dataSourceObjectService;
    }

    @GetMapping("/by-path")
    public DataSourceObjectService.DataSourceView get(@RequestParam String path) {
        return dataSourceObjectService.getByPath(path);
    }

    @PostMapping
    public DataSourceObjectService.DataSourceView create(@RequestBody CreateDataSourceRequest request) {
        return dataSourceObjectService.create(request.toWriteRequest());
    }

    @PutMapping("/by-path")
    public DataSourceObjectService.DataSourceView update(
            @RequestParam String path,
            @RequestBody UpdateDataSourceRequest request
    ) {
        return dataSourceObjectService.update(path, request.toWriteRequest());
    }

    @PostMapping("/by-path/test-connection")
    public Map<String, Object> testConnection(
            @RequestParam String path,
            @RequestBody(required = false) TestConnectionRequest request
    ) {
        DataSourceConnectionResolver.ExternalConfigProbe probe = request != null
                ? request.toProbe()
                : null;
        ConnectionTestResult result = dataSourceObjectService.testConnection(path, probe);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", path);
        body.put("connected", result.connected());
        if (result.message() != null) {
            body.put("message", result.message());
        }
        return body;
    }

    @PostMapping("/by-path/execute-query")
    public DataSourceQueryResult executeQuery(
            @RequestParam String path,
            @RequestBody ExecuteQueryRequest request
    ) {
        return dataSourceObjectService.executeQuery(
                path,
                request.query(),
                request.params(),
                request.maxRows()
        );
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
