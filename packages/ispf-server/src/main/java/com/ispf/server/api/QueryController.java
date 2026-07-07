package com.ispf.server.api;

import com.ispf.server.query.QueryDefinitionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/queries")
public class QueryController {

    private final QueryDefinitionService queryDefinitionService;

    public QueryController(QueryDefinitionService queryDefinitionService) {
        this.queryDefinitionService = queryDefinitionService;
    }

    @GetMapping
    public List<QueryDefinitionService.QueryDefinition> list() {
        return queryDefinitionService.list();
    }

    @GetMapping("/by-path")
    public QueryDefinitionService.QueryDefinition get(@RequestParam String path) {
        return queryDefinitionService.getByPath(path);
    }

    @PostMapping
    public QueryDefinitionService.QueryDefinition create(@RequestBody SaveQueryRequest request) {
        if (request.queryId() == null || request.queryId().isBlank()) {
            throw new IllegalArgumentException("queryId is required");
        }
        return queryDefinitionService.create(toDefinition("", request));
    }

    @PutMapping("/by-path")
    public QueryDefinitionService.QueryDefinition update(
            @RequestParam String path,
            @RequestBody SaveQueryRequest request
    ) {
        String queryId = request.queryId() != null && !request.queryId().isBlank()
                ? request.queryId()
                : path.substring(path.lastIndexOf('.') + 1);
        return queryDefinitionService.update(path, toDefinition(queryId, request));
    }

    @DeleteMapping("/by-path")
    public Map<String, Object> delete(@RequestParam String path) {
        queryDefinitionService.delete(path);
        return Map.of("status", "deleted", "path", path);
    }

    private static QueryDefinitionService.QueryDefinition toDefinition(String queryId, SaveQueryRequest request) {
        String id = queryId != null && !queryId.isBlank() ? queryId : request.queryId();
        return new QueryDefinitionService.QueryDefinition(
                "",
                id,
                request.displayName(),
                request.description(),
                request.queryType() != null ? request.queryType() : "tree-scan",
                request.sourcePathPattern() != null ? request.sourcePathPattern() : "",
                request.fieldsJson() != null ? request.fieldsJson() : "[]",
                request.filterExpression() != null ? request.filterExpression() : "",
                request.enabled() == null || request.enabled(),
                "",
                ""
        );
    }

    public record SaveQueryRequest(
            String queryId,
            String displayName,
            String description,
            String queryType,
            String sourcePathPattern,
            String fieldsJson,
            String filterExpression,
            Boolean enabled
    ) {
    }
}
