package com.ispf.server.api;

import com.ispf.server.binding.SqlBindingObjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sql-bindings")
public class SqlBindingController {

    private final SqlBindingObjectService sqlBindingObjectService;

    public SqlBindingController(SqlBindingObjectService sqlBindingObjectService) {
        this.sqlBindingObjectService = sqlBindingObjectService;
    }

    @GetMapping("/by-path")
    public SqlBindingObjectService.BindingDefinition get(@RequestParam String path) {
        return sqlBindingObjectService.getByPath(path);
    }

    @PostMapping
    public SqlBindingObjectService.BindingDefinition create(@RequestBody SaveSqlBindingRequest request) {
        if (request.bindingId() == null || request.bindingId().isBlank()) {
            throw new IllegalArgumentException("bindingId is required");
        }
        return sqlBindingObjectService.create(toDefinition("", request));
    }

    @PutMapping("/by-path")
    public SqlBindingObjectService.BindingDefinition update(
            @RequestParam String path,
            @RequestBody SaveSqlBindingRequest request
    ) {
        return sqlBindingObjectService.update(path, toDefinition(path, request));
    }

    @PostMapping("/by-path/refresh")
    public Map<String, Object> refresh(@RequestParam String path) {
        sqlBindingObjectService.refresh(path);
        return Map.of(
                "status", "OK",
                "path", path,
                "binding", sqlBindingObjectService.getByPath(path)
        );
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
