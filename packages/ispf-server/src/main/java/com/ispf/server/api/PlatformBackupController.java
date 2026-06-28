package com.ispf.server.api;

import com.ispf.server.platform.PlatformBackupService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/backup")
public class PlatformBackupController {

    private final PlatformBackupService platformBackupService;

    public PlatformBackupController(PlatformBackupService platformBackupService) {
        this.platformBackupService = platformBackupService;
    }

    @GetMapping("/export")
    public Map<String, Object> export(Authentication authentication) {
        return platformBackupService.exportSubtree();
    }

    @PostMapping("/import")
    public PlatformBackupService.ImportResult importBackup(
            Authentication authentication,
            @RequestBody String json,
            @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        return platformBackupService.importSubtree(json, dryRun);
    }
}
