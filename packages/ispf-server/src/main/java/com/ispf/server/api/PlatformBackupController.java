package com.ispf.server.api;

import com.ispf.server.platform.PlatformBackupService;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/platform/backup")
public class PlatformBackupController {

    private static final long EXPORT_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(2);
    private static final int MAX_CONCURRENT_EXPORTS = 2;

    private final PlatformBackupService platformBackupService;
    private final Semaphore exportSlots = new Semaphore(MAX_CONCURRENT_EXPORTS);
    private final Set<String> activeExportPrincipals = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, Long> lastExportNanos = new ConcurrentHashMap<>();

    public PlatformBackupController(PlatformBackupService platformBackupService) {
        this.platformBackupService = platformBackupService;
    }

    @GetMapping("/export")
    public Map<String, Object> export(
            Authentication authentication,
            @RequestParam(required = false) String rootPath
    ) {
        String principal = authentication == null || authentication.getName() == null
                ? "anonymous"
                : authentication.getName();
        if (!activeExportPrincipals.add(principal)) {
            throw tooManyExportRequests();
        }
        boolean slotAcquired = false;
        try {
            long now = System.nanoTime();
            if (lastExportNanos.size() > 1024) {
                lastExportNanos.entrySet().removeIf(
                        entry -> now - entry.getValue() >= EXPORT_COOLDOWN_NANOS
                );
            }
            lastExportNanos.compute(principal, (key, previous) -> {
                if (previous != null && now - previous < EXPORT_COOLDOWN_NANOS) {
                    throw tooManyExportRequests();
                }
                return now;
            });
            slotAcquired = exportSlots.tryAcquire();
            if (!slotAcquired) {
                throw tooManyExportRequests();
            }
            if (rootPath == null || rootPath.isBlank()) {
                return platformBackupService.exportSubtree();
            }
            return platformBackupService.exportSubtree(rootPath);
        } finally {
            if (slotAcquired) {
                exportSlots.release();
            }
            activeExportPrincipals.remove(principal);
        }
    }

    private static ResponseStatusException tooManyExportRequests() {
        return new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Export already running or requested too frequently"
        );
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
