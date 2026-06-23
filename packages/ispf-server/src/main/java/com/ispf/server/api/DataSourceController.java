package com.ispf.server.api;

import com.ispf.server.datasource.DataSourceObjectService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        return dataSourceObjectService.create(
                request.name(),
                request.displayName(),
                request.schemaName(),
                request.description()
        );
    }

    @PutMapping("/by-path")
    public DataSourceObjectService.DataSourceView update(
            @RequestParam String path,
            @RequestBody UpdateDataSourceRequest request
    ) {
        return dataSourceObjectService.update(
                path,
                request.displayName(),
                request.schemaName(),
                request.description()
        );
    }

    public record CreateDataSourceRequest(
            @NotBlank String name,
            String displayName,
            @NotBlank String schemaName,
            String description
    ) {
    }

    public record UpdateDataSourceRequest(
            String displayName,
            String schemaName,
            String description
    ) {
    }
}
