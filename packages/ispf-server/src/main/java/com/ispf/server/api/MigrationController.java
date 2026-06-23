package com.ispf.server.api;

import com.ispf.server.migration.MigrationObjectService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/migrations")
public class MigrationController {

    private final MigrationObjectService migrationObjectService;

    public MigrationController(MigrationObjectService migrationObjectService) {
        this.migrationObjectService = migrationObjectService;
    }

    @GetMapping("/by-path")
    public MigrationObjectService.MigrationView get(@RequestParam String path) {
        return migrationObjectService.getByPath(path);
    }

    @PostMapping
    public MigrationObjectService.MigrationView create(@RequestBody CreateMigrationRequest request) {
        return migrationObjectService.create(
                request.scriptId(),
                request.version(),
                request.dataSourcePath(),
                request.sql()
        );
    }

    @PutMapping("/by-path")
    public MigrationObjectService.MigrationView update(
            @RequestParam String path,
            @RequestBody UpdateMigrationRequest request
    ) {
        return migrationObjectService.update(
                path,
                request.scriptId(),
                request.version(),
                request.dataSourcePath(),
                request.sql()
        );
    }

    @PostMapping("/by-path/apply")
    public Map<String, Object> apply(@RequestParam String path) {
        migrationObjectService.applyByPath(path);
        return Map.of(
                "status", "OK",
                "path", path,
                "migration", migrationObjectService.getByPath(path)
        );
    }

    public record CreateMigrationRequest(
            @NotBlank String scriptId,
            String version,
            String dataSourcePath,
            String sql
    ) {
    }

    public record UpdateMigrationRequest(
            String scriptId,
            String version,
            String dataSourcePath,
            String sql
    ) {
    }
}
